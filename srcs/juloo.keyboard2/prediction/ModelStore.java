package juloo.keyboard2.prediction;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** One immutable model. Network access is used only by an explicit settings action. */
public final class ModelStore
{
  public static final long SIZE = 105453536L;
  public static final String SHA256 = "b38d0237afc63fa77f35b9f82f394ac2116e77609514461d6bc51deec02a9c91";
  public static final String URL_STRING = "https://huggingface.co/QuantFactory/SmolLM2-135M-GGUF/resolve/d948db3614be18259a175aafd7689a70f1cb4e2f/SmolLM2-135M.Q4_K_M.gguf";
  public static volatile boolean downloading, failed, runtimeFailed;
  public static volatile long downloaded;
  private static final AtomicBoolean cancelled = new AtomicBoolean();
  private static volatile HttpURLConnection connection;

  public static File file(Context context)
  {
    return new File(context.getNoBackupFilesDir(), "prediction-smollm2-135m-q4km.gguf");
  }

  public static boolean supported()
  {
    if (Build.VERSION.SDK_INT < 28) return false;
    for (String abi : Build.SUPPORTED_ABIS) if (abi.equals("arm64-v8a")) return true;
    return false;
  }

  public static synchronized void download(Context context)
  {
    if (downloading) return;
    final File target = file(context.getApplicationContext());
    downloading = true; failed = false; runtimeFailed = false; downloaded = 0;
    cancelled.set(false);
    new Thread(() -> {
      File temp = new File(target.getPath() + ".part");
      try
      {
        if (target.getParentFile().getUsableSpace() < SIZE + 16 * 1024 * 1024)
          throw new IOException("Insufficient storage");
        URL url = new URL(URL_STRING);
        // Permit HTTPS redirects to the model host's object storage, never HTTP.
        for (int redirects = 0; ; redirects++)
        {
          if (cancelled.get()) throw new IOException("Cancelled");
          HttpURLConnection c = (HttpURLConnection)url.openConnection();
          connection = c;
          c.setConnectTimeout(15000); c.setReadTimeout(15000);
          c.setInstanceFollowRedirects(false);
          int status = c.getResponseCode();
          if (status >= 300 && status < 400 && redirects < 5)
          {
            URL next = new URL(url, c.getHeaderField("Location"));
            c.disconnect();
            if (!"https".equals(next.getProtocol())) throw new IOException("Invalid redirect");
            url = next; continue;
          }
          if (status != 200) throw new IOException("Download failed");
          break;
        }
        try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp))
        {
          ModelVerifier.copy(in, out, SIZE, SHA256, cancelled, bytes -> downloaded = bytes);
          out.getFD().sync();
        }
        synchronized (ModelStore.class)
        {
          if (cancelled.get() || !temp.renameTo(target)) throw new IOException("Install failed");
        }
      }
      catch (Exception e) { failed = !cancelled.get(); }
      finally
      {
        HttpURLConnection c = connection;
        if (c != null) c.disconnect();
        connection = null;
        temp.delete(); downloading = false;
      }
    }, "prediction-model-download").start();
  }

  public static synchronized void cancel()
  {
    cancelled.set(true);
    // Read/connect timeouts bound termination; never block the UI on disconnect.
  }

  public static synchronized boolean remove(Context context)
  {
    if (downloading) return false;
    File f = file(context);
    runtimeFailed = false;
    return !f.exists() || f.delete();
  }

}
