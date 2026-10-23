#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>

static unsigned non_writable_vuid_count;
static unsigned validation_error_count;

static VKAPI_ATTR VkBool32 VKAPI_CALL validation_callback(
        VkDebugUtilsMessageSeverityFlagBitsEXT severity,
        VkDebugUtilsMessageTypeFlagsEXT types,
        const VkDebugUtilsMessengerCallbackDataEXT *data,
        void *user_data) {
    (void) severity;
    (void) types;
    (void) user_data;
    if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
        validation_error_count++;
    }
    if (data != NULL && data->pMessage != NULL &&
            ((data->pMessageIdName != NULL &&
              strcmp(data->pMessageIdName, "VUID-RuntimeSpirv-NonWritable-06341") == 0) ||
             strstr(data->pMessage, "vertexPipelineStoresAndAtomics was not enabled") != NULL)) {
        non_writable_vuid_count++;
    }
    if (data != NULL && data->pMessage != NULL) {
        fprintf(stderr, "[Lavapipe feature probe] %s\n", data->pMessage);
    }
    return VK_FALSE;
}

static int fail(const char *message, VkResult result) {
    fprintf(stderr, "Lavapipe feature probe failed: %s (VkResult %d)\n", message, result);
    return 1;
}

#define CHECK_VK(call, message) do { \
    VkResult result__ = (call); \
    if (result__ != VK_SUCCESS) return fail((message), result__); \
} while (0)

static uint32_t *read_spirv(const char *path, size_t *word_count) {
    FILE *file = fopen(path, "rb");
    if (file == NULL) return NULL;
    if (fseek(file, 0, SEEK_END) != 0) { fclose(file); return NULL; }
    long length = ftell(file);
    if (length <= 0 || (length % 4) != 0 || fseek(file, 0, SEEK_SET) != 0) {
        fclose(file);
        return NULL;
    }
    uint32_t *words = malloc((size_t) length);
    if (words == NULL) { fclose(file); return NULL; }
    size_t read_count = fread(words, 1, (size_t) length, file);
    fclose(file);
    if (read_count != (size_t) length) { free(words); return NULL; }
    *word_count = (size_t) length / sizeof(uint32_t);
    return words;
}

int main(int argc, char **argv) {
    int use_vulkanmod_pnext = 0;
    int use_storage_image = 0;
    if (argc < 2 || argc > 4) {
        return fail("usage: lavapipe_feature_probe <vertex.spv> [--vulkanmod-pnext] [--storage-image]", VK_ERROR_INITIALIZATION_FAILED);
    }
    for (int i = 2; i < argc; i++) {
        if (strcmp(argv[i], "--vulkanmod-pnext") == 0) {
            use_vulkanmod_pnext = 1;
        } else if (strcmp(argv[i], "--storage-image") == 0) {
            use_storage_image = 1;
        } else {
            return fail("unknown probe option", VK_ERROR_INITIALIZATION_FAILED);
        }
    }

    const char *layers[] = {"VK_LAYER_KHRONOS_validation"};
    const char *extensions[] = {VK_EXT_DEBUG_UTILS_EXTENSION_NAME};
    VkDebugUtilsMessengerCreateInfoEXT debug_info = {
        .sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT,
        .messageSeverity = VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                           VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT,
        .messageType = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                       VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                       VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT,
        .pfnUserCallback = validation_callback,
    };
    VkApplicationInfo application_info = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "Veyra Lavapipe Feature Probe",
        .apiVersion = VK_API_VERSION_1_2,
    };
    VkInstanceCreateInfo instance_info = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pNext = &debug_info,
        .pApplicationInfo = &application_info,
        .enabledLayerCount = 1,
        .ppEnabledLayerNames = layers,
        .enabledExtensionCount = 1,
        .ppEnabledExtensionNames = extensions,
    };
    VkInstance instance = VK_NULL_HANDLE;
    CHECK_VK(vkCreateInstance(&instance_info, NULL, &instance), "vkCreateInstance");

    PFN_vkCreateDebugUtilsMessengerEXT create_debug_messenger =
            (PFN_vkCreateDebugUtilsMessengerEXT) vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT");
    PFN_vkDestroyDebugUtilsMessengerEXT destroy_debug_messenger =
            (PFN_vkDestroyDebugUtilsMessengerEXT) vkGetInstanceProcAddr(instance, "vkDestroyDebugUtilsMessengerEXT");
    VkDebugUtilsMessengerEXT messenger = VK_NULL_HANDLE;
    if (create_debug_messenger == NULL || destroy_debug_messenger == NULL) {
        vkDestroyInstance(instance, NULL);
        return fail("VK_EXT_debug_utils entry points are unavailable", VK_ERROR_EXTENSION_NOT_PRESENT);
    }
    CHECK_VK(create_debug_messenger(instance, &debug_info, NULL, &messenger), "vkCreateDebugUtilsMessengerEXT");

    uint32_t physical_device_count = 0;
    CHECK_VK(vkEnumeratePhysicalDevices(instance, &physical_device_count, NULL), "vkEnumeratePhysicalDevices(count)");
    if (physical_device_count == 0) return fail("no Vulkan physical device found", VK_ERROR_INITIALIZATION_FAILED);
    VkPhysicalDevice *physical_devices = calloc(physical_device_count, sizeof(VkPhysicalDevice));
    if (physical_devices == NULL) return fail("allocate physical-device array", VK_ERROR_OUT_OF_HOST_MEMORY);
    CHECK_VK(vkEnumeratePhysicalDevices(instance, &physical_device_count, physical_devices), "vkEnumeratePhysicalDevices(list)");
    VkPhysicalDevice physical_device = physical_devices[0];
    free(physical_devices);
    VkPhysicalDeviceProperties physical_device_properties;
    vkGetPhysicalDeviceProperties(physical_device, &physical_device_properties);

    VkPhysicalDeviceFeatures supported_features = {0};
    vkGetPhysicalDeviceFeatures(physical_device, &supported_features);
    if (supported_features.vertexPipelineStoresAndAtomics != VK_TRUE) {
        return fail("Lavapipe does not support vertexPipelineStoresAndAtomics", VK_ERROR_FEATURE_NOT_PRESENT);
    }
    VkPhysicalDeviceVulkan11Features supported_vulkan11 = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES,
    };
    VkPhysicalDeviceDynamicRenderingFeaturesKHR supported_dynamic_rendering = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR,
    };
    VkPhysicalDeviceFeatures2 supported_features2 = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2,
        .pNext = &supported_vulkan11,
    };
    if (use_vulkanmod_pnext) {
        supported_vulkan11.pNext = &supported_dynamic_rendering;
        vkGetPhysicalDeviceFeatures2(physical_device, &supported_features2);
        if (supported_dynamic_rendering.dynamicRendering != VK_TRUE) {
            return fail("Lavapipe does not support dynamicRendering", VK_ERROR_FEATURE_NOT_PRESENT);
        }
    }

    uint32_t queue_family_count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(physical_device, &queue_family_count, NULL);
    VkQueueFamilyProperties *queue_families = calloc(queue_family_count, sizeof(VkQueueFamilyProperties));
    if (queue_families == NULL) return fail("allocate queue-family array", VK_ERROR_OUT_OF_HOST_MEMORY);
    vkGetPhysicalDeviceQueueFamilyProperties(physical_device, &queue_family_count, queue_families);
    uint32_t graphics_queue_family = UINT32_MAX;
    for (uint32_t i = 0; i < queue_family_count; i++) {
        if (queue_families[i].queueCount > 0 && (queue_families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
            graphics_queue_family = i;
            break;
        }
    }
    free(queue_families);
    if (graphics_queue_family == UINT32_MAX) return fail("no graphics queue family found", VK_ERROR_INITIALIZATION_FAILED);

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queue_info = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = graphics_queue_family,
        .queueCount = 1,
        .pQueuePriorities = &priority,
    };
    VkPhysicalDeviceFeatures enabled_features = {0};
    enabled_features.vertexPipelineStoresAndAtomics = VK_TRUE;
    VkPhysicalDeviceDynamicRenderingFeaturesKHR dynamic_rendering_features = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR,
        .dynamicRendering = VK_TRUE,
    };
    VkPhysicalDeviceVulkan11Features vulkan11_features = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES,
        .pNext = &dynamic_rendering_features,
        .shaderDrawParameters = supported_vulkan11.shaderDrawParameters,
    };
    const char *device_extensions[] = {VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME};
    VkDeviceCreateInfo device_info = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .pNext = use_vulkanmod_pnext ? &vulkan11_features : NULL,
        .queueCreateInfoCount = 1,
        .pQueueCreateInfos = &queue_info,
        .enabledExtensionCount = use_vulkanmod_pnext ? 1 : 0,
        .ppEnabledExtensionNames = use_vulkanmod_pnext ? device_extensions : NULL,
        .pEnabledFeatures = &enabled_features,
    };
    VkDevice device = VK_NULL_HANDLE;
    CHECK_VK(vkCreateDevice(physical_device, &device_info, NULL, &device), "vkCreateDevice(pEnabledFeatures=true)");
    fprintf(stdout, "pEnabledFeatures.vertexPipelineStoresAndAtomics=%s\n",
            enabled_features.vertexPipelineStoresAndAtomics == VK_TRUE ? "true" : "false");
    fprintf(stdout, "VkDeviceCreateInfo.pNext=%s\n",
            use_vulkanmod_pnext
                    ? "VkPhysicalDeviceVulkan11Features(shaderDrawParameters=supported) -> VkPhysicalDeviceDynamicRenderingFeaturesKHR(dynamicRendering=true)"
                    : "NULL");
    fprintf(stdout, "Physical device: %s\n", physical_device_properties.deviceName);

    size_t spirv_word_count = 0;
    uint32_t *spirv = read_spirv(argv[1], &spirv_word_count);
    if (spirv == NULL) return fail("read compiled vertex SPIR-V", VK_ERROR_INITIALIZATION_FAILED);
    VkShaderModuleCreateInfo shader_info = {
        .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
        .codeSize = spirv_word_count * sizeof(uint32_t),
        .pCode = spirv,
    };
    VkShaderModule shader = VK_NULL_HANDLE;
    VkResult shader_result = vkCreateShaderModule(device, &shader_info, NULL, &shader);
    free(spirv);
    CHECK_VK(shader_result, "vkCreateShaderModule");

    VkDescriptorSetLayoutBinding storage_binding = {
        .binding = 0,
        .descriptorType = use_storage_image
                ? VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                : VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        .descriptorCount = 1,
        .stageFlags = VK_SHADER_STAGE_VERTEX_BIT,
    };
    VkDescriptorSetLayoutCreateInfo descriptor_layout_info = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
        .bindingCount = 1,
        .pBindings = &storage_binding,
    };
    VkDescriptorSetLayout descriptor_layout = VK_NULL_HANDLE;
    CHECK_VK(vkCreateDescriptorSetLayout(device, &descriptor_layout_info, NULL, &descriptor_layout), "vkCreateDescriptorSetLayout");
    VkPipelineLayoutCreateInfo pipeline_layout_info = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
        .setLayoutCount = 1,
        .pSetLayouts = &descriptor_layout,
    };
    VkPipelineLayout pipeline_layout = VK_NULL_HANDLE;
    CHECK_VK(vkCreatePipelineLayout(device, &pipeline_layout_info, NULL, &pipeline_layout), "vkCreatePipelineLayout");

    VkAttachmentDescription attachment = {
        .format = VK_FORMAT_R8G8B8A8_UNORM,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
        .storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
        .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
    };
    VkAttachmentReference attachment_ref = {
        .attachment = 0,
        .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
    };
    VkSubpassDescription subpass = {
        .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1,
        .pColorAttachments = &attachment_ref,
    };
    VkRenderPassCreateInfo render_pass_info = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1,
        .pAttachments = &attachment,
        .subpassCount = 1,
        .pSubpasses = &subpass,
    };
    VkRenderPass render_pass = VK_NULL_HANDLE;
    CHECK_VK(vkCreateRenderPass(device, &render_pass_info, NULL, &render_pass), "vkCreateRenderPass");

    VkPipelineShaderStageCreateInfo stage = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
        .stage = VK_SHADER_STAGE_VERTEX_BIT,
        .module = shader,
        .pName = "main",
    };
    VkPipelineVertexInputStateCreateInfo vertex_input = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
    };
    VkPipelineInputAssemblyStateCreateInfo input_assembly = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
    };
    VkViewport viewport = {0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};
    VkRect2D scissor = {{0, 0}, {1, 1}};
    VkPipelineViewportStateCreateInfo viewport_state = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1,
        .pViewports = &viewport,
        .scissorCount = 1,
        .pScissors = &scissor,
    };
    VkPipelineRasterizationStateCreateInfo rasterization = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .rasterizerDiscardEnable = VK_TRUE,
        .polygonMode = VK_POLYGON_MODE_FILL,
        .cullMode = VK_CULL_MODE_NONE,
        .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE,
        .lineWidth = 1.0f,
    };
    VkPipelineMultisampleStateCreateInfo multisample = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT,
    };
    VkPipelineColorBlendAttachmentState blend_attachment = {0};
    VkPipelineColorBlendStateCreateInfo color_blend = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1,
        .pAttachments = &blend_attachment,
    };
    VkGraphicsPipelineCreateInfo pipeline_info = {
        .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
        .stageCount = 1,
        .pStages = &stage,
        .pVertexInputState = &vertex_input,
        .pInputAssemblyState = &input_assembly,
        .pViewportState = &viewport_state,
        .pRasterizationState = &rasterization,
        .pMultisampleState = &multisample,
        .pColorBlendState = &color_blend,
        .layout = pipeline_layout,
        .renderPass = render_pass,
        .subpass = 0,
    };
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkResult pipeline_result = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &pipeline_info, NULL, &pipeline);
    if (pipeline_result != VK_SUCCESS) return fail("vkCreateGraphicsPipelines", pipeline_result);
    if (validation_error_count != 0) {
        fprintf(stderr, "Lavapipe emitted %u validation error(s), including %u VUID-RuntimeSpirv-NonWritable-06341 message(s)\n",
                validation_error_count, non_writable_vuid_count);
        return 1;
    }
    fprintf(stdout, "%s vertex pipeline created; validation error count=%u\n",
            use_storage_image ? "Storage-image atomic" : "Storage-buffer write", validation_error_count);

    vkDestroyPipeline(device, pipeline, NULL);
    vkDestroyRenderPass(device, render_pass, NULL);
    vkDestroyPipelineLayout(device, pipeline_layout, NULL);
    vkDestroyDescriptorSetLayout(device, descriptor_layout, NULL);
    vkDestroyShaderModule(device, shader, NULL);
    vkDestroyDevice(device, NULL);
    destroy_debug_messenger(instance, messenger, NULL);
    vkDestroyInstance(instance, NULL);
    return 0;
}
