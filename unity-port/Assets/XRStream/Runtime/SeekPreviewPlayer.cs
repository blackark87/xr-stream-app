using System;
using UnityEngine;
using UnityEngine.Video;

namespace XRStream
{
    // A muted decoder renders the preview to a small GPU texture.
    // Main playback stays at its original position until the drag is committed.
    public sealed class SeekPreviewPlayer : MonoBehaviour
    {
        VideoPlayer player;
        GameObject decoder;
        RenderTexture texture;
        readonly VideoSeekState seek = new();
        double requestedTarget, duration;
        bool active, firstFramePending;
        public event Action<RenderTexture> FrameReady;
        public event Action<string> Error;
        public void Begin(string url, double target, double videoDuration)
        {
            End();
            active = true; duration = videoDuration; requestedTarget = NormalizeTarget(target, duration);
            decoder = new GameObject("Seek Preview Decoder"); decoder.transform.SetParent(transform, false);
            player = decoder.AddComponent<VideoPlayer>();
            player.playOnAwake = false; player.source = VideoSource.Url;
            player.renderMode = VideoRenderMode.RenderTexture;
            player.audioOutputMode = VideoAudioOutputMode.None; player.controlledAudioTrackCount = 0;
            player.waitForFirstFrame = true; player.sendFrameReadyEvents = true;
            player.prepareCompleted += Prepared; player.seekCompleted += SeekCompleted;
            player.frameReady += Presented; player.errorReceived += Failed;
            player.url = url; player.Prepare();
        }
        public static double NormalizeTarget(double target, double duration) =>
            Math.Clamp(Math.Floor(Math.Max(0, double.IsFinite(target) ? target : 0) / 2 + .5) * 2,
                0, Math.Max(0, duration - .01));
        public void Request(double target)
        {
            if (!active) return;
            var next = NormalizeTarget(target, duration);
            if (Math.Abs(next - requestedTarget) < .001) return;
            requestedTarget = next;
            if (player.isPrepared && !firstFramePending) SeekToLatest();
        }
        void Prepared(VideoPlayer source)
        {
            if (!active || source != player) return;
            if (!source.canSetTime) { Failed(source, "Preview seeking is unsupported."); return; }
            if (source.width == 0 || source.height == 0) { Failed(source, "Preview dimensions are unavailable."); return; }
            var aspect = source.height > 0 ? (double)source.width / source.height : 16d / 9;
            var width = Math.Clamp((int)Math.Round(270 * aspect), 1, 960);
            var height = Math.Clamp((int)Math.Round(width / aspect), 1, 270);
            texture = new RenderTexture(width, height, 0, RenderTextureFormat.ARGB32)
            { name = "XRStream Seek Preview", filterMode = FilterMode.Bilinear };
            if (!texture.Create()) { Failed(source, "Preview texture allocation failed."); return; }
            source.targetTexture = texture;
            if (requestedTarget <= .001) { firstFramePending = true; source.Play(); }
            else SeekToLatest();
        }
        void SeekToLatest()
        {
            if (seek.Request(requestedTarget, false)) StartSeek(requestedTarget);
        }
        void StartSeek(double target)
        {
            player.Pause(); player.sendFrameReadyEvents = true; player.time = target;
        }
        void SeekCompleted(VideoPlayer source)
        {
            if (!active || source != player || !seek.Active) return;
            seek.MarkOperationCompleted(); source.Play();
        }
        void Presented(VideoPlayer source, long frame)
        {
            if (!active || source != player) return;
            if (firstFramePending)
            {
                firstFramePending = false; source.Pause();
                if (requestedTarget > .001) { SeekToLatest(); return; }
            }
            else
            {
                if (!seek.PresentFrame(out var next)) return;
                if (next.HasValue) { StartSeek(next.Value); return; }
                source.Pause();
            }
            source.sendFrameReadyEvents = false;
            RuntimeDiagnostics.Log("seek-preview-frame", $"target={requestedTarget:F2} position={source.time:F2} size={texture.width}x{texture.height}");
            FrameReady?.Invoke(texture);
        }
        void Failed(VideoPlayer source, string message)
        {
            if (!active || source != player) return;
            RuntimeDiagnostics.Log("seek-preview-error", message);
            End(); Error?.Invoke(message);
        }
        public void End()
        {
            active = false; firstFramePending = false; seek.Reset();
            if (player != null)
            {
                player.prepareCompleted -= Prepared; player.seekCompleted -= SeekCompleted;
                player.frameReady -= Presented; player.errorReceived -= Failed;
                player.Stop(); player.targetTexture = null; player = null;
            }
            if (decoder != null) { Destroy(decoder); decoder = null; }
            if (texture != null) { texture.Release(); Destroy(texture); texture = null; }
        }
        void OnDestroy() => End();
    }
}
