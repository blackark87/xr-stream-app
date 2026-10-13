using System;

namespace XRStream.Editor
{
    public static class PreviewChecks
    {
        public static object Run()
        {
            Check(SeekPreviewPlayer.NormalizeTarget(-10, 100) == 0);
            Check(SeekPreviewPlayer.NormalizeTarget(15.2, 100) == 16);
            Check(SeekPreviewPlayer.NormalizeTarget(1, 100) == 2);
            Check(Math.Abs(SeekPreviewPlayer.NormalizeTarget(1000, 24) - 23.99) < .00001);
            Check(SeekPreviewPlayer.NormalizeTarget(double.NaN, 100) == 0);
            Check(SeekPreviewPlayer.NormalizeTarget(10, 0) == 0);
            return new { passed = 6 };
        }
        static void Check(bool condition) { if (!condition) throw new InvalidOperationException("Seek preview target validation failed."); }
    }
}
