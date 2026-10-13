using System;
using System.Collections.Generic;
using System.Collections.Concurrent;
using UnityEngine;

namespace XRStream
{
    [Serializable] public class BridgeRequest
    {
        public string id, operation, path = "", name = "", address = "", share = "", username = "", password = "", domain = "";
        public string format = "", stereo = "";
        public long generation, serverId, positionMs, durationMs;
        public int port = 445;
        public bool network;
    }
    [Serializable] public class ServerItem { public long id; public int port = 445; public string name, address, share, username, domain; public bool local; }
    [Serializable] public class FileItem { public string name, path, subtitlePath; public bool directory, video; public long size; }
    [Serializable] public class SubtitleColor { public int start, end; public long argb; }
    [Serializable] public class SubtitleLine { public string text; public SubtitleColor[] colors; }
    [Serializable] public class SubtitleFrame { public long startMs, endMs; public SubtitleLine[] lines; }
    [Serializable] public class BridgeData
    {
        public ServerItem[] servers = Array.Empty<ServerItem>();
        public FileItem[] files = Array.Empty<FileItem>();
        public SubtitleFrame[] subtitleFrames = Array.Empty<SubtitleFrame>();
        public string token, url, format, stereo;
        public long serverId, positionMs;
        public bool cancelled;
    }
    [Serializable] public class BridgeResponse
    {
        public string id, error;
        public long generation;
        public bool success;
        public BridgeData data;
    }

    public sealed class AndroidBridgeClient : MonoBehaviour
    {
        AndroidJavaClass bridge;
        AndroidJavaObject activity;
        readonly Dictionary<string, Action<BridgeResponse>> pending = new();
        readonly ConcurrentQueue<Action> continuations = new();
        long generation;
        public event Action<string> Error;
        public bool Available => bridge != null;
        public PlaybackPreferences LoadPlaybackPreferences()
        {
            var json = Available ? bridge.CallStatic<string>("getPlaybackPreferences") :
                PlayerPrefs.GetString("XRStream.PlaybackPreferences", "");
            var value = string.IsNullOrEmpty(json) ? new PlaybackPreferences() : JsonUtility.FromJson<PlaybackPreferences>(json);
            value.Normalize(); return value;
        }
        public void SavePlaybackPreferences(PlaybackPreferences value)
        {
            value.Normalize();
            var json = JsonUtility.ToJson(value);
            if (Available) bridge.CallStatic("setPlaybackPreferences", json);
            else { PlayerPrefs.SetString("XRStream.PlaybackPreferences", json); PlayerPrefs.Save(); }
        }

        void Awake()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            using var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
            activity = player.GetStatic<AndroidJavaObject>("currentActivity");
            bridge = new AndroidJavaClass("blackark.app.vr.unity.UnityBridge");
            bridge.CallStatic("initialize", activity);
#endif
        }
        public void BeginNavigation()
        {
            foreach (var id in pending.Keys) bridge?.CallStatic("cancel", id);
            pending.Clear();
            generation++;
        }
        public void Request(BridgeRequest request, Action<BridgeResponse> callback)
        {
            if (!Available) { Error?.Invoke("Local/SMB browsing is available in the Android app."); return; }
            request.id = Guid.NewGuid().ToString("N");
            request.generation = generation;
            if (callback != null) pending[request.id] = callback;
#if UNITY_ANDROID && !UNITY_EDITOR
            if (request.network && bridge.CallStatic<bool>("requiresLocalNetworkPermission"))
            {
                var permissions = new UnityEngine.Android.PermissionCallbacks();
                permissions.PermissionGranted += _ => continuations.Enqueue(() => Submit(request));
                permissions.PermissionDenied += _ => continuations.Enqueue(() => PermissionDenied(request));
                UnityEngine.Android.Permission.RequestUserPermission("android.permission.ACCESS_LOCAL_NETWORK", permissions);
                return;
            }
#endif
            Submit(request);
        }
        void Submit(BridgeRequest request)
        {
            if (!Available || request.generation != generation) return;
            bridge.CallStatic("submit", JsonUtility.ToJson(request));
        }
        void PermissionDenied(BridgeRequest request)
        {
            if (!Available || request.generation != generation) return;
            const string message = "Allow Nearby devices permission in Android app settings to connect to SMB.";
            if (pending.Remove(request.id, out var callback))
                callback(new BridgeResponse { id = request.id, generation = generation, success = false, error = message });
            else Error?.Invoke(message);
        }
        public void ChooseLocalFolder(Action<BridgeResponse> callback)
        {
            if (!Available) { Error?.Invoke("SAF folder selection is available in the Android app."); return; }
            var id = Guid.NewGuid().ToString("N");
            pending[id] = callback;
            bridge.CallStatic("chooseLocalFolder", activity, id, generation);
        }
        public void ReleaseStream(string token)
        {
            if (!string.IsNullOrEmpty(token)) bridge?.CallStatic("releaseStream", token);
        }
        void Update()
        {
            if (!Available) return;
            while (continuations.TryDequeue(out var continuation)) continuation();
            for (var index = 0; index < 16; index++)
            {
                var json = bridge.CallStatic<string>("poll");
                if (string.IsNullOrEmpty(json)) break;
                var response = JsonUtility.FromJson<BridgeResponse>(json);
                if (response.generation != generation)
                {
                    ReleaseStream(response.data?.token);
                    if (response.generation < 0) Error?.Invoke(response.error);
                    continue;
                }
                if (!string.IsNullOrEmpty(response.id) && pending.Remove(response.id, out var callback))
                    callback(response);
                else if (!response.success) Error?.Invoke(response.error);
            }
        }
        void OnDestroy()
        {
            BeginNavigation();
            bridge?.Dispose();
            activity?.Dispose();
            bridge = null;
            activity = null;
        }
    }
}
