using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Video;

namespace XRStream
{
    public enum VideoFormat { Format2D, Format180, Format360 }
    public enum StereoMode { Mono, SideBySide, TopBottom }

    public interface IVideoPlayback
    {
        bool Prepared { get; }
        bool Playing { get; }
        double Position { get; }
        double Duration { get; }
        Texture Texture { get; }
        void Play();
        void Pause();
        void Seek(double seconds, bool resume);
        void Stop();
    }
    public sealed class UnityVideoBackend : MonoBehaviour, IVideoPlayback
    {
        VideoPlayer player;
        AudioSource audio;
        readonly List<AudioSource> audioSources = new();
        RenderTexture texture;
        double initialPosition;
        readonly VideoSeekState seek = new();
        bool muteBeforeSeek;
        float prepareStarted, seekStarted;
        public event Action Ready, Ended;
        public event Action<string> Error;
        public bool Prepared => player != null && player.isPrepared;
        public bool Playing => Prepared && player.isPlaying;
        public bool Seeking => seek.Active;
        public bool WantsPlayback => Seeking ? seek.Resume : Playing;
        public double DisplayPosition => Seeking ? seek.LatestTarget : Position;
        public double Position => Prepared ? Math.Max(0, player.time) : 0;
        public double Duration => Prepared ? player.length : 0;
        public Texture Texture => texture;
        public string SourceUrl => player != null ? player.url : "";
        public int AudioTrackCount => Prepared ? Math.Min(player.audioTrackCount, player.controlledAudioTrackCount) : 0;
        public int SelectedAudioTrack { get; private set; }
        public float Volume { get => audio.volume; set { foreach (var output in audioSources) output.volume = Mathf.Clamp01(value); } }
        public float Speed { get => player.playbackSpeed; set { if (player.canSetPlaybackSpeed) player.playbackSpeed = value; } }

        void Awake()
        {
            audio = gameObject.AddComponent<AudioSource>();
            audio.playOnAwake = false;
            audio.spatialBlend = 0;
            audioSources.Add(audio);
            player = gameObject.AddComponent<VideoPlayer>();
            player.playOnAwake = false;
            player.source = VideoSource.Url;
            player.renderMode = VideoRenderMode.RenderTexture;
            player.audioOutputMode = VideoAudioOutputMode.AudioSource;
            player.controlledAudioTrackCount = VideoPlayer.controlledAudioTrackMaxCount;
            player.EnableAudioTrack(0, true);
            player.SetTargetAudioSource(0, audio);
            player.waitForFirstFrame = true;
            player.skipOnDrop = true;
            player.prepareCompleted += OnPrepared;
            player.seekCompleted += OnSeekCompleted;
            player.loopPointReached += OnEnded;
            player.errorReceived += OnError;
            player.frameReady += OnFrameReady;
        }
        public void Open(string url, double resumeSeconds)
        {
            Stop();
            initialPosition = Math.Max(0, resumeSeconds);
            prepareStarted = Time.realtimeSinceStartup;
            RuntimeDiagnostics.Log("video-prepare", $"resumeSeconds={initialPosition:F2}");
            player.url = url;
            player.sendFrameReadyEvents = Debug.isDebugBuild;
            player.Prepare();
        }
        void OnPrepared(VideoPlayer source)
        {
            SelectedAudioTrack = 0;
            for (ushort track = 0; track < AudioTrackCount; track++)
            {
                if (track >= audioSources.Count)
                {
                    var output = gameObject.AddComponent<AudioSource>();
                    output.playOnAwake = false; output.spatialBlend = 0; output.volume = audio.volume;
                    audioSources.Add(output);
                }
                source.SetTargetAudioSource(track, audioSources[track]);
                source.EnableAudioTrack(track, track == 0);
            }
            var width = (int)source.width;
            var height = (int)source.height;
            if (width <= 0 || height <= 0 || width > SystemInfo.maxTextureSize || height > SystemInfo.maxTextureSize)
            { OnError(source, "Decoded video dimensions exceed this device's GPU texture support."); return; }
            texture = new RenderTexture(width, height, 0, RenderTextureFormat.ARGB32) { name = "XRStream Video", filterMode = FilterMode.Bilinear };
            if (!texture.Create()) { OnError(source, "Unable to allocate the video GPU texture."); return; }
            source.targetTexture = texture;
            RuntimeDiagnostics.Log("video-prepared", $"size={width}x{height} duration={source.length:F2} fps={source.frameRate:F2} " +
                $"audioTracks={source.audioTrackCount} seek={source.canSetTime} speed={source.canSetPlaybackSpeed} " +
                $"textureCreated={texture.IsCreated()} elapsedSeconds={Time.realtimeSinceStartup - prepareStarted:F2}");
            Ready?.Invoke();
            if (initialPosition > 0 && source.canSetTime) Seek(initialPosition, true);
            else source.Play();
        }
        public string AudioTrackLabel => AudioTrackCount == 0 ? "Audio: none" :
            $"Audio {SelectedAudioTrack + 1}/{AudioTrackCount} ({player.GetAudioLanguageCode((ushort)SelectedAudioTrack)})";
        public void NextAudioTrack()
        {
            if (!Prepared || Seeking || AudioTrackCount < 2) return;
            var resume = Playing;
            player.Pause();
            SelectedAudioTrack = (SelectedAudioTrack + 1) % AudioTrackCount;
            for (ushort track = 0; track < AudioTrackCount; track++) player.EnableAudioTrack(track, track == SelectedAudioTrack);
            RuntimeDiagnostics.Log("audio-track", $"selected={SelectedAudioTrack} total={AudioTrackCount}");
            if (resume) player.Play();
        }
        void MuteAudio(bool muted) { foreach (var output in audioSources) output.mute = muted; }
        public void Play()
        {
            if (!Prepared) return;
            if (!Seeking && Duration > 0 && Position >= Duration - .1)
            { Seek(0, true); return; }
            seek.Resume = true;
            if (!Seeking || seek.OperationCompleted) player.Play();
            RuntimeDiagnostics.Log("video-play", $"position={DisplayPosition:F2}");
        }
        public void Pause()
        {
            seek.Resume = false;
            if (!Prepared) return;
            // A completed seek still needs its new frame to be presented before pausing.
            if (!Seeking || !seek.OperationCompleted) player.Pause();
            RuntimeDiagnostics.Log("video-pause", $"position={DisplayPosition:F2}");
        }
        public void Seek(double seconds, bool resume)
        {
            if (!Prepared || !player.canSetTime) return;
            var target = Math.Clamp(seconds, 0, Math.Max(0, Duration - .01));
            if (!seek.Request(target, resume))
            {
                RuntimeDiagnostics.Log("video-seek-queued", $"latest={target:F2} resume={resume}");
                return;
            }
            muteBeforeSeek = audio.mute;
            MuteAudio(true);
            StartSeek(target);
        }
        void StartSeek(double target)
        {
            seekStarted = Time.realtimeSinceStartup;
            player.Pause();
            player.sendFrameReadyEvents = true;
            RuntimeDiagnostics.Log("video-seek", $"from={Position:F2} to={target:F2} resume={seek.Resume}");
            player.time = target;
        }
        void OnSeekCompleted(VideoPlayer source)
        {
            if (!Seeking) return;
            seek.MarkOperationCompleted();
            RuntimeDiagnostics.Log("video-seek-decoded", $"target={seek.Target:F2} elapsedSeconds={Time.realtimeSinceStartup - seekStarted:F2}");
            // Unity time/texture settle on frameReady, after seekCompleted. Pump one muted frame
            // even for paused scrubbing, then restore the user's requested playback state.
            source.Play();
        }
        void OnFrameReady(VideoPlayer source, long frame)
        {
            if (Seeking)
            {
                if (!seek.PresentFrame(out var next)) return;
                RuntimeDiagnostics.Log("video-seek-complete", $"frame={frame} position={Position:F2} elapsedSeconds={Time.realtimeSinceStartup - seekStarted:F2}");
                if (next.HasValue) { StartSeek(next.Value); return; }
                MuteAudio(muteBeforeSeek);
                if (!seek.Resume) source.Pause();
            }
            else RuntimeDiagnostics.Log("video-first-frame", $"frame={frame} position={Position:F2}");
            source.sendFrameReadyEvents = false;
        }
        void OnEnded(VideoPlayer source) { RuntimeDiagnostics.Log("video-ended", $"position={Position:F2}"); Ended?.Invoke(); }
        void OnError(VideoPlayer source, string error) { RuntimeDiagnostics.Log("video-error", error); Stop(); Error?.Invoke(error); }
        public void Stop()
        {
            if (seek.Active && audio != null) MuteAudio(muteBeforeSeek);
            seek.Reset();
            if (player != null) { player.Stop(); player.targetTexture = null; }
            if (texture != null) { texture.Release(); Destroy(texture); texture = null; }
        }
        void OnDestroy()
        {
            Stop();
            player.prepareCompleted -= OnPrepared;
            player.seekCompleted -= OnSeekCompleted;
            player.loopPointReached -= OnEnded;
            player.errorReceived -= OnError;
            player.frameReady -= OnFrameReady;
        }
    }
}
