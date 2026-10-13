using System;

namespace XRStream.Editor
{
    public static class SeekStateChecks
    {
        public static object Run()
        {
            var seek = new VideoSeekState();
            Check(seek.Request(97.32, true), "First seek must start.");
            Check(!seek.Request(145.15, seek.Resume), "Second seek must stay pending.");
            Check(!seek.Request(92.65, seek.Resume), "Latest drag must replace the pending destination.");
            Check(seek.Resume && seek.LatestTarget == 92.65, "Repeated seek must preserve playback and latest destination.");
            Check(!seek.PresentFrame(out _), "A stale frame cannot complete the native seek.");
            seek.MarkOperationCompleted();
            Check(seek.PresentFrame(out var next) && next == 92.65 && seek.Active, "Display the completed frame before starting only the latest seek.");
            seek.Resume = false;
            seek.MarkOperationCompleted();
            Check(seek.PresentFrame(out next) && next == null && !seek.Active && !seek.Resume, "A user's pause must survive seek completion.");
            seek.Request(200, true); seek.Request(300, true); seek.Request(200, true);
            seek.MarkOperationCompleted();
            Check(seek.PresentFrame(out next) && next == null, "Returning to the active destination must cancel obsolete queued seeks.");
            seek.Request(400, true); seek.Request(500, true); seek.Reset();
            Check(!seek.Active && seek.QueuedTarget == null && !seek.Resume, "Changing video must discard all seek state.");
            return new { passed = 9 };
        }
        static void Check(bool condition, string message) { if (!condition) throw new InvalidOperationException(message); }
    }
}
