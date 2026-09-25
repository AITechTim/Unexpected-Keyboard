package juloo.keyboard2.prediction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Streaming verification with a hard size limit; never buffers the model in RAM. */
public final class ModelVerifier
{
  public interface Progress { void received(long bytes); }

  public static void copy(InputStream in, OutputStream out, long expectedSize,
      String expectedHash, AtomicBoolean cancelled, Progress progress) throws IOException
  {
    MessageDigest digest;
    try { digest = MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    byte[] buffer = new byte[65536];
    long total = 0;
    int n;
    while ((n = in.read(buffer)) != -1)
    {
      if (cancelled.get() || n > expectedSize - total) throw new IOException("Download interrupted");
      out.write(buffer, 0, n); digest.update(buffer, 0, n); total += n;
      progress.received(total);
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : digest.digest())
      hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    if (cancelled.get() || total != expectedSize || !hex.toString().equals(expectedHash))
      throw new IOException("Invalid model checksum");
  }
}
