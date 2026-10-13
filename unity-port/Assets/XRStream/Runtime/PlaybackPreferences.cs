using System;
using UnityEngine;

namespace XRStream
{
    [Serializable]
    public sealed class PlaybackPreferences
    {
        public string subtitleSize = "Medium";
        public float subtitleDistance = 1.5f, subtitleVertical = .15f, uiHorizontal;
        public float minDistance = .7f, maxDistance = 2f, defaultDistance = 1.5f;
        public float minVertical = -.3f, maxVertical = .3f, defaultVertical = .15f;
        public float minHorizontal = -.3f, maxHorizontal = .3f, defaultHorizontal;
        public float controlsAutoHideSeconds = 10;
        public static readonly string[] Sizes = { "Small", "Medium", "Large", "ExtraLarge" };
        public float SubtitleSizeScale => subtitleSize switch
        {
            "Small" => .8f, "Large" => 1.25f, "ExtraLarge" => 1.5f, _ => 1f
        };
        public void Normalize()
        {
            if (Array.IndexOf(Sizes, subtitleSize) < 0) subtitleSize = "Medium";
            subtitleDistance = FiniteClamp(subtitleDistance, minDistance, maxDistance, defaultDistance);
            subtitleDistance = Mathf.Clamp(Mathf.Round(subtitleDistance * 10) / 10, minDistance, maxDistance);
            subtitleVertical = FiniteClamp(subtitleVertical, minVertical, maxVertical, defaultVertical);
            uiHorizontal = FiniteClamp(uiHorizontal, minHorizontal, maxHorizontal, defaultHorizontal);
        }
        public void ResetSubtitles()
        {
            subtitleSize = "Medium"; subtitleDistance = defaultDistance;
            subtitleVertical = defaultVertical; uiHorizontal = defaultHorizontal;
            Normalize();
        }
        static float FiniteClamp(float value, float min, float max, float fallback) =>
            Mathf.Clamp(float.IsFinite(value) ? value : fallback, min, max);
    }
}
