import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Per-channel RGB mean absolute error (0..255) between two 640x360 captures over the
 * full frame, the generated material wall and the floor rows. Run with
 * {@code java scripts/CompareFrames.java veyra.png iris.png}.
 */
public class CompareFrames {
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        var a = ImageIO.read(Path.of(args[0]).toFile());
        var b = ImageIO.read(Path.of(args[1]).toFile());
        if (a.getWidth() != 640 || a.getHeight() != 360 || b.getWidth() != 640 || b.getHeight() != 360)
            throw new AssertionError("Both captures must be 640x360");
        String[] names = {"full", "wall", "floor"};
        int[][] boxes = {{0, 0, 640, 360}, {25, 8, 615, 245}, {0, 250, 640, 360}};
        for (int i = 0; i < boxes.length; i++) {
            int[] r = boxes[i];
            long n = (long) (r[2] - r[0]) * (r[3] - r[1]);
            double[] am = new double[3], bm = new double[3];
            long diff = 0;
            for (int y = r[1]; y < r[3]; y++) for (int x = r[0]; x < r[2]; x++) for (int c = 0; c < 3; c++) {
                int av = (a.getRGB(x, y) >> (16 - 8 * c)) & 255, bv = (b.getRGB(x, y) >> (16 - 8 * c)) & 255;
                am[c] += av;
                bm[c] += bv;
                diff += Math.abs(av - bv);
            }
            for (int c = 0; c < 3; c++) { am[c] /= n; bm[c] /= n; }
            System.out.printf("%s %.6f %s %s%n", names[i], diff / (3.0 * n), Arrays.toString(am), Arrays.toString(bm));
        }
    }
}
