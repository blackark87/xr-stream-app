using System.Collections.Generic;
using UnityEngine;
using UnityEngine.XR;

namespace XRStream
{
    public sealed class RuntimeDiagnostics : MonoBehaviour
    {
        readonly List<XRDisplaySubsystem> displays = new();
        string lastDisplayState;
        float nextDisplayCheck;

        void Start()
        {
            Log("startup", $"unity={Application.unityVersion} app={Application.version} os={SystemInfo.operatingSystem} " +
                $"device={SystemInfo.deviceModel} graphics={SystemInfo.graphicsDeviceType} gpu={SystemInfo.graphicsDeviceName} " +
                $"maxTexture={SystemInfo.maxTextureSize} colorSpace={QualitySettings.activeColorSpace}");
        }
        void Update()
        {
#if DEVELOPMENT_BUILD || UNITY_EDITOR
            if (Time.unscaledTime < nextDisplayCheck) return;
            nextDisplayCheck = Time.unscaledTime + 1;
            displays.Clear();
            SubsystemManager.GetSubsystems(displays);
            var state = "displays=" + displays.Count;
            foreach (var display in displays) state += $" running={display.running}";
            if (state == lastDisplayState) return;
            lastDisplayState = state;
            Log("xr-display", state);
#endif
        }
        [System.Diagnostics.Conditional("DEVELOPMENT_BUILD")]
        [System.Diagnostics.Conditional("UNITY_EDITOR")]
        public static void Log(string stage, string details) => Debug.Log($"[XRStream] {stage}: {details}");
    }
}
