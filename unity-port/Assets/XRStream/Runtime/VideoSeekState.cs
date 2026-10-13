namespace XRStream
{
    // One native seek at a time. Repeated user input replaces only the pending destination.
    public sealed class VideoSeekState
    {
        public bool Active { get; private set; }
        public bool OperationCompleted { get; private set; }
        public bool Resume { get; set; }
        public double Target { get; private set; }
        public double? QueuedTarget { get; private set; }
        public double LatestTarget => QueuedTarget ?? Target;

        public bool Request(double target, bool resume)
        {
            Resume = resume;
            if (Active)
            {
                QueuedTarget = System.Math.Abs(target - Target) < .001 ? null : target;
                return false;
            }
            Active = true;
            OperationCompleted = false;
            Target = target;
            return true;
        }
        public void MarkOperationCompleted() { if (Active) OperationCompleted = true; }
        public bool PresentFrame(out double? next)
        {
            next = null;
            if (!Active || !OperationCompleted) return false;
            OperationCompleted = false;
            if (QueuedTarget.HasValue)
            {
                next = Target = QueuedTarget.Value;
                QueuedTarget = null;
            }
            else Active = false;
            return true;
        }
        public void Reset()
        {
            Active = false;
            OperationCompleted = false;
            Resume = false;
            QueuedTarget = null;
        }
    }
}
