using System;

namespace XRStream.Editor
{
    public static class ControlsStateChecks
    {
        public static object Run()
        {
            var controls = new PlaybackControlsState();
            controls.Reset(0);
            Check(!controls.Tick(9.9, true, false, false) && controls.Visible);
            Check(controls.Tick(10, true, false, false) && !controls.Visible);
            Check(controls.Toggle(11, false) && controls.Visible);
            Check(!controls.Toggle(11.1, false) && controls.Visible);
            controls.BeginInteraction(12); controls.BeginInteraction(12);
            Check(!controls.Toggle(13, false) && controls.InteractionLocks == 2);
            controls.EndInteraction(13);
            Check(!controls.Tick(30, true, false, false) && controls.Visible && controls.InteractionLocks == 1);
            controls.EndInteraction(31);
            Check(!controls.Toggle(31.1, false));
            Check(!controls.Tick(50, true, true, false) && controls.Visible);
            Check(!controls.Tick(80, true, false, true) && controls.Visible);
            Check(controls.Tick(90, true, false, false) && !controls.Visible);
            Check(controls.Tick(91, false, false, false) && controls.Visible);
            controls.Reset(92);
            Check(controls.InteractionLocks == 0 && controls.Visible);
            return new { passed = 12 };
        }
        static void Check(bool condition) { if (!condition) throw new InvalidOperationException("Playback controls state validation failed."); }
    }
}
