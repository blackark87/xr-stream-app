using System;
using UnityEngine;

namespace XRStream.Editor
{
    public static class SubtitleChecks
    {
        public static object Run()
        {
            var frames = new[] {
                new SubtitleFrame { startMs = 1000, endMs = 3000 },
                new SubtitleFrame { startMs = 5000, endMs = 7000 }
            };
            Check(SubtitlePresentation.FindFrame(frames, 999) == -1);
            Check(SubtitlePresentation.FindFrame(frames, 1000) == 0);
            Check(SubtitlePresentation.FindFrame(frames, 3000) == -1);
            Check(SubtitlePresentation.FindFrame(frames, 5000) == 1);
            Check(SubtitlePresentation.FindFrame(frames, 2000) == 0);
            Check(SubtitlePresentation.FindFrame(Array.Empty<SubtitleFrame>(), 5000) == -1);
            var font = Resources.Load<Font>("Fonts/NotoSansKR-Regular");
            Check(font != null && font.HasCharacter('한') && font.HasCharacter('글'));
            var preferences = new PlaybackPreferences { subtitleSize = "invalid", subtitleDistance = float.NaN,
                subtitleVertical = 50, uiHorizontal = float.NegativeInfinity };
            preferences.Normalize();
            Check(preferences.subtitleSize == "Medium" && preferences.subtitleDistance == 1.5f &&
                preferences.subtitleVertical == .3f && preferences.uiHorizontal == 0);
            preferences.subtitleSize = "ExtraLarge"; preferences.subtitleDistance = 1.74f; preferences.Normalize();
            Check(preferences.SubtitleSizeScale == 1.5f && Mathf.Approximately(preferences.subtitleDistance, 1.7f));
            preferences.ResetSubtitles();
            Check(preferences.subtitleSize == "Medium" && preferences.subtitleVertical == .15f && preferences.subtitleDistance == 1.5f);
            return new { passed = 10 };
        }
        static void Check(bool condition) { if (!condition) throw new InvalidOperationException("Subtitle timeline/font validation failed."); }
    }
}
