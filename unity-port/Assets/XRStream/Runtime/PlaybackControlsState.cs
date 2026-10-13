using System;

namespace XRStream
{
    // Mirrors the original interaction lock, recent-input suppression and auto-hide policy.
    public sealed class PlaybackControlsState
    {
        public bool Visible { get; private set; } = true;
        public int InteractionLocks { get; private set; }
        double lastInteraction, suppressUntil;
        public double AutoHideSeconds { get; set; } = 10;
        public void Reset(double now)
        {
            InteractionLocks = 0; suppressUntil = now;
            Show(now);
        }
        public void Touch(double now) { lastInteraction = now; suppressUntil = now + .3; }
        public void Show(double now) { Visible = true; Touch(now); }
        public bool Toggle(double now, bool seeking)
        {
            if (InteractionLocks > 0 || seeking || now < suppressUntil) return false;
            Visible = !Visible; Touch(now); return true;
        }
        public void BeginInteraction(double now) { InteractionLocks++; Show(now); }
        public void EndInteraction(double now) { InteractionLocks = Math.Max(0, InteractionLocks - 1); Touch(now); }
        public bool Tick(double now, bool playing, bool menuOpen, bool seeking)
        {
            if (!playing || menuOpen || seeking || InteractionLocks > 0)
            {
                var changed = !Visible; Visible = true; lastInteraction = now; return changed;
            }
            if (!Visible || now < suppressUntil || now - lastInteraction < AutoHideSeconds) return false;
            Visible = false; return true;
        }
    }
}
