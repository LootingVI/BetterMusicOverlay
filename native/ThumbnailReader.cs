using System;
using System.Threading;
using System.IO;
using Windows.Storage.Streams;
using Windows.Foundation;

// Typed WinRT projection: PowerShell exposes the thumbnail stream as an untyped COM object.
public static class ThumbnailReader {
    public static string Read(object raw) {
        IRandomAccessStream stream = (IRandomAccessStream)raw;
        if (stream.Size == 0 || stream.Size > 1500000) return "";
        DataReader reader = new DataReader(stream.GetInputStreamAt(0));
        reader.InputStreamOptions = InputStreamOptions.Partial;
        try {
            using (var memory = new MemoryStream()) {
                DateTime deadline = DateTime.UtcNow.AddSeconds(3);
                while ((ulong)memory.Length < stream.Size) {
                    var operation = reader.LoadAsync((uint)(stream.Size - (ulong)memory.Length));
                    while (operation.Status == AsyncStatus.Started) {
                        if (DateTime.UtcNow > deadline) { operation.Cancel(); throw new TimeoutException("Artwork read timed out"); }
                        Thread.Sleep(10);
                    }
                    uint length = operation.GetResults();
                    if (length == 0) break;
                    byte[] bytes = new byte[length];
                    reader.ReadBytes(bytes);
                    memory.Write(bytes, 0, bytes.Length);
                }
                return Convert.ToBase64String(memory.ToArray());
            }
        } finally { reader.Dispose(); stream.Dispose(); }
    }
}
