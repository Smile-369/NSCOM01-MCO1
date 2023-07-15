import java.io.FileOutputStream;
import java.io.IOException;

public class TestFileCreator {
    public static void main(String[] args) {
        String filePath = "cccc.bin"; // Specify the path and filename for the test file

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            // Create a byte array with sample data
            byte[] data = { 0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x20, 0x57, 0x6F, 0x72, 0x6C, 0x64 };

            // Write the data to the file
            fos.write(data);

            System.out.println("Test file created successfully.");
        } catch (IOException e) {
            System.out.println("An error occurred while creating the test file: " + e.getMessage());
        }
    }
}