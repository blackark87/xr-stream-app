using System;
using System.Text;
using UnityEngine;
using UnityEngine.UI;

namespace XRStream
{
    public sealed class SubtitlePresentation : MonoBehaviour
    {
        Camera viewer;
        UnityVideoBackend playback;
        Text text;
        SubtitleFrame[] frames = Array.Empty<SubtitleFrame>();
        int shown = -1;
        bool enabledByUser = true;
        PlaybackPreferences preferences;
        public bool EnabledByUser => enabledByUser;
        public bool Available => frames.Length > 0;

        public void Initialize(Camera camera, UnityVideoBackend backend)
        {
            viewer = camera; playback = backend;
            var canvas = gameObject.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.WorldSpace; canvas.worldCamera = viewer;
            var rect = GetComponent<RectTransform>();
            rect.sizeDelta = new Vector2(1400, 250); rect.localScale = Vector3.one * .0016f;
            var ui = new SpatialUi();
            text = ui.Label(rect, "", 0, 0, 1400, 250, 40);
            text.font = Resources.Load<Font>("Fonts/NotoSansKR-Regular");
            if (text.font == null) throw new InvalidOperationException("Korean subtitle font asset is missing.");
            text.alignment = TextAnchor.MiddleCenter;
            text.supportRichText = true;
            var shadow = text.gameObject.AddComponent<Shadow>();
            shadow.effectColor = Color.black; shadow.effectDistance = new Vector2(2, -2);
            RuntimeDiagnostics.Log("subtitle-font", $"korean={text.font.HasCharacter('한')}");
        }
        public void SetFrames(SubtitleFrame[] value)
        {
            frames = value ?? Array.Empty<SubtitleFrame>(); shown = -1; text.text = "";
            RuntimeDiagnostics.Log("subtitle-loaded", $"intervals={frames.Length}");
        }
        public void Toggle() { enabledByUser = !enabledByUser; shown = -1; text.text = ""; }
        public void ApplyPreferences(PlaybackPreferences value)
        {
            preferences = value;
            text.fontSize = Mathf.RoundToInt(40 * value.SubtitleSizeScale);
            GetComponent<RectTransform>().localScale = Vector3.one * (.0016f * value.subtitleDistance / 1.75f);
        }
        public void Clear() { frames = Array.Empty<SubtitleFrame>(); shown = -1; enabledByUser = true; if (text != null) text.text = ""; }
        public static int FindFrame(SubtitleFrame[] frames, long timeMs)
        {
            int low = 0, high = frames.Length - 1, found = -1;
            while (low <= high)
            {
                var middle = (low + high) / 2;
                if (frames[middle].startMs <= timeMs) { found = middle; low = middle + 1; }
                else high = middle - 1;
            }
            return found >= 0 && timeMs < frames[found].endMs ? found : -1;
        }
        void LateUpdate()
        {
            if (text == null) return;
            var yaw = Quaternion.Euler(0, viewer.transform.eulerAngles.y, 0);
            var distance = preferences?.subtitleDistance ?? 1.5f;
            var vertical = preferences?.subtitleVertical ?? .15f;
            var horizontal = preferences?.uiHorizontal ?? 0;
            transform.SetPositionAndRotation(viewer.transform.position +
                yaw * new Vector3(horizontal, -.35f + vertical, distance), yaw);
            var index = enabledByUser && playback.Prepared && !playback.Seeking ? FindFrame(frames, (long)(playback.Position * 1000)) : -1;
            if (index == shown) return;
            shown = index;
            if (index < 0) { text.text = ""; return; }
            var builder = new StringBuilder();
            foreach (var line in frames[index].lines)
            {
                if (builder.Length > 0) builder.Append('\n');
                AppendLine(builder, line);
            }
            text.text = builder.ToString();
        }
        static void AppendLine(StringBuilder builder, SubtitleLine line)
        {
            var raw = line.text ?? "";
            long? previous = null;
            for (var index = 0; index < raw.Length; index++)
            {
                long? color = null;
                foreach (var run in line.colors ?? Array.Empty<SubtitleColor>())
                    if (index >= run.start && index < run.end) color = run.argb;
                if (color != previous)
                {
                    if (previous.HasValue) builder.Append("</color>");
                    if (color.HasValue)
                    {
                        var rgba = ((uint)color.Value << 8) | ((uint)color.Value >> 24);
                        builder.Append("<color=#").Append(rgba.ToString("X8")).Append('>');
                    }
                    previous = color;
                }
                builder.Append(raw[index]);
                // Prevent subtitle text from introducing Unity rich-text directives.
                if (raw[index] == '<') builder.Append('\u200b');
            }
            if (previous.HasValue) builder.Append("</color>");
        }
    }
}
