using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.XR;
using Unity.XR.CoreUtils;

namespace XRStream.Editor
{
    public static class XRStreamSceneBuilder
    {
        public const string ScenePath = "Assets/XRStream/Scenes/XRStream.unity";
        public static object CreateScene()
        {
            if (EditorApplication.isPlaying) throw new InvalidOperationException("Stop Play mode before creating the port scene.");
            if (EditorSceneManager.GetActiveScene().isDirty) throw new InvalidOperationException("Save the current Unity scene first.");
            if (!File.Exists(ScenePath))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(ScenePath));
                var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
                var originObject = new GameObject("XR Origin");
                var offset = new GameObject("Camera Offset"); offset.transform.SetParent(originObject.transform, false);
                var cameraObject = new GameObject("XR Camera", typeof(Camera), typeof(AudioListener));
                cameraObject.tag = "MainCamera"; cameraObject.transform.SetParent(offset.transform, false);
                var camera = cameraObject.GetComponent<Camera>();
                camera.clearFlags = CameraClearFlags.SolidColor; camera.backgroundColor = Color.black;
                camera.nearClipPlane = .05f; camera.farClipPlane = 30;
                var pose = cameraObject.AddComponent<TrackedPoseDriver>();
                pose.positionInput = new InputActionProperty(new InputAction("Head Position", InputActionType.Value, "<XRHMD>/centerEyePosition"));
                pose.rotationInput = new InputActionProperty(new InputAction("Head Rotation", InputActionType.Value, "<XRHMD>/centerEyeRotation"));
                pose.trackingStateInput = new InputActionProperty(new InputAction("Head Tracking", InputActionType.Value, "<XRHMD>/trackingState"));
                var origin = originObject.AddComponent<XROrigin>();
                origin.Camera = camera; origin.CameraFloorOffsetObject = offset;
                origin.RequestedTrackingOriginMode = XROrigin.TrackingOriginMode.NotSpecified;
                var app = new GameObject("XR Stream").AddComponent<XRStreamApp>();
                app.viewer = camera; app.trackingSpace = offset.transform;
                EditorSceneManager.SaveScene(scene, ScenePath);
            }
            else EditorSceneManager.OpenScene(ScenePath);
            // The video player is positioned relative to the head; it does not require a floor origin.
            var appInScene = UnityEngine.Object.FindFirstObjectByType<XRStreamApp>();
            var appOrigin = appInScene.viewer.GetComponentInParent<XROrigin>();
            appOrigin.RequestedTrackingOriginMode = XROrigin.TrackingOriginMode.NotSpecified;
            EditorUtility.SetDirty(appOrigin);
            EditorSceneManager.SaveScene(EditorSceneManager.GetActiveScene(), ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) }
                .Concat(EditorBuildSettings.scenes.Where(scene => scene.path != ScenePath)
                    .Select(scene => new EditorBuildSettingsScene(scene.path, false))).ToArray();
            foreach (var path in AssetDatabase.FindAssets("", new[] { "Assets/XRStream/Plugins/Android" }).Select(AssetDatabase.GUIDToAssetPath))
            {
                if (AssetImporter.GetAtPath(path) is not PluginImporter plugin) continue;
                plugin.SetCompatibleWithAnyPlatform(false);
                plugin.SetCompatibleWithEditor(false);
                plugin.SetCompatibleWithPlatform(BuildTarget.Android, true);
                plugin.SaveAndReimport();
            }
            AssetDatabase.SaveAssets();
            return new { scene = ScenePath, applicationId = PlayerSettings.GetApplicationIdentifier(UnityEditor.Build.NamedBuildTarget.Android) };
        }
        public static object BuildAndroid(string output)
        {
            if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.Android)
                throw new InvalidOperationException("Switch the active Unity build target to Android first.");
            var issues = new System.Collections.Generic.List<UnityEngine.XR.OpenXR.Features.OpenXRFeature.ValidationRule>();
            UnityEditor.XR.OpenXR.OpenXRProjectValidation.GetCurrentValidationIssues(issues, BuildTargetGroup.Android);
            if (issues.Any(issue => issue.error)) throw new InvalidOperationException(string.Join("\n", issues.Where(issue => issue.error).Select(issue => issue.message)));
            if (PlayerSettings.GetApplicationIdentifier(UnityEditor.Build.NamedBuildTarget.Android) != "blackark.app.vr.unity")
                throw new InvalidOperationException("The Unity app ID must be blackark.app.vr.unity for parallel installation.");
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output)));
            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions {
                scenes = new[] { ScenePath }, locationPathName = output,
                target = BuildTarget.Android, options = BuildOptions.Development
            });
            return new { result = report.summary.result.ToString(), errors = report.summary.totalErrors,
                warnings = report.summary.totalWarnings, bytes = report.summary.totalSize, output = report.summary.outputPath };
        }
    }
}
