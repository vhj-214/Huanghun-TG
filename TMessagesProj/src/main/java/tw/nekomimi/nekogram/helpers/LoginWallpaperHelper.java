package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Persists the optional user-selected login wallpaper in app-private storage. */
public final class LoginWallpaperHelper {
    private static final String FILE_NAME = "huanghun_login_wallpaper.jpg";
    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;

    private LoginWallpaperHelper() { }

    public static File getFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static boolean isSet(Context context) {
        File file = getFile(context);
        return file.isFile() && file.length() > 0;
    }

    public static boolean save(Context context, Uri source) {
        if (source == null) return false;
        File target = getFile(context);
        File temporary = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (InputStream input = context.getContentResolver().openInputStream(source);
             OutputStream output = new FileOutputStream(temporary)) {
            if (input == null) return false;
            byte[] buffer = new byte[32 * 1024];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_SIZE) {
                    temporary.delete();
                    return false;
                }
                output.write(buffer, 0, count);
            }
            output.flush();
            if (total == 0) {
                temporary.delete();
                return false;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                temporary.delete();
                return false;
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception error) {
            temporary.delete();
            return false;
        }
    }

    public static boolean clear(Context context) {
        File file = getFile(context);
        return !file.exists() || file.delete();
    }
}
