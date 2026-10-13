using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem;
using UnityEngine.XR.Interaction.Toolkit;
using UnityEngine.XR.Interaction.Toolkit.UI;

namespace XRStream
{
    public sealed class XRStreamApp : MonoBehaviour
    {
        public Camera viewer;
        public Transform trackingSpace;
        AndroidBridgeClient bridge;
        UnityVideoBackend playback;
        SubtitlePresentation subtitles;
        SeekPreviewPlayer previewPlayer;
        RectTransform previewCard;
        RawImage previewImage;
        Text previewTime, previewMessage;
        VideoProjection projection;
        PlaybackPreferences preferences;
        SpatialUi ui;
        RectTransform canvas, browser, bar, display, subtitleSettings;
        Text status, title, currentTime, totalTime, playLabel, recenterLabel;
        Text audioLabel, subtitleLabel;
        Button audioButton, subtitleButton;
        Text subtitleSizeLabel, subtitleDistanceLabel, subtitleVerticalLabel, uiHorizontalLabel;
        Slider subtitleDistanceSlider, subtitleVerticalSlider, uiHorizontalSlider;
        PlaybackTimeline timeline;
        Button playButton;
        ServerItem[] servers = Array.Empty<ServerItem>();
        ServerItem browsingServer, playingServer;
        FileItem[] files = Array.Empty<FileItem>(), queue = Array.Empty<FileItem>();
        FileItem current;
        string folder = "", token;
        readonly Stack<string> parents = new();
        VideoFormat format;
        StereoMode stereo;
        bool wasPlayingBeforeDrag, appPauseWasPlaying, autoInfer;
        float lastSave;
        float preferencesSaveAt;
        bool preferencesDirty, settingsInputActive;
        readonly PlaybackControlsState controls = new();
        Coroutine recenter;

        void Start()
        {
            if (viewer == null || trackingSpace == null) throw new InvalidOperationException("The XR camera and tracking space must be assigned.");
            gameObject.AddComponent<RuntimeDiagnostics>();
            bridge = gameObject.AddComponent<AndroidBridgeClient>();
            preferences = bridge.LoadPlaybackPreferences();
            controls.AutoHideSeconds = preferences.controlsAutoHideSeconds;
            playback = gameObject.AddComponent<UnityVideoBackend>();
            previewPlayer = gameObject.AddComponent<SeekPreviewPlayer>();
            var surface = new GameObject("Video Projection"); surface.transform.SetParent(transform, false);
            projection = surface.AddComponent<VideoProjection>(); projection.Initialize(viewer);
            bridge.Error += ShowError; playback.Error += PlaybackError;
            playback.Ready += VideoReady; playback.Ended += VideoEnded;
            CreateInput(); CreateUi();
            previewPlayer.FrameReady += PreviewReady;
            previewPlayer.Error += error =>
            { previewImage.texture = null; previewMessage.text = "Preview unavailable"; };
            var captions = new GameObject("Subtitles", typeof(RectTransform));
            captions.transform.SetParent(transform, false);
            subtitles = captions.AddComponent<SubtitlePresentation>(); subtitles.Initialize(viewer, playback);
            subtitles.ApplyPreferences(preferences);
            LoadServers();
        }
        void CreateInput()
        {
            gameObject.AddComponent<XRInteractionManager>();
            var events = new GameObject("XR Event System", typeof(EventSystem), typeof(XRUIInputModule));
            events.transform.SetParent(transform, false);
            var module = events.GetComponent<XRUIInputModule>();
            module.enableXRInput = true;
            module.enableBuiltinActionsAsFallback = true;
#if UNITY_EDITOR
            module.enableMouseInput = true;
#else
            module.enableMouseInput = false;
#endif
            foreach (var hand in new[] { "LeftHand", "RightHand" })
            foreach (var handTracking in new[] { false, true })
            {
                var pointer = new GameObject(hand + (handTracking ? " Hand Ray" : " Controller Ray"));
                pointer.transform.SetParent(transform, false);
                var input = pointer.AddComponent<SpatialPointer>();
                input.Initialize(trackingSpace, hand, handTracking); input.Pressed += PointerPressed;
            }
        }
        void CreateUi()
        {
            ui = new SpatialUi();
            canvas = new GameObject("XRStream Canvas", typeof(RectTransform), typeof(Canvas)).GetComponent<RectTransform>();
            canvas.SetParent(transform, false); canvas.sizeDelta = new Vector2(1400, 1080); canvas.localScale = Vector3.one * .0016f;
            var worldCanvas = canvas.GetComponent<Canvas>(); worldCanvas.renderMode = RenderMode.WorldSpace; worldCanvas.worldCamera = viewer;
            canvas.gameObject.AddComponent<TrackedDeviceGraphicRaycaster>();
#if UNITY_EDITOR
            canvas.gameObject.AddComponent<GraphicRaycaster>();
#endif
            browser = ui.Panel("Browser", canvas, 0, 0, 1400, 910);
            bar = ui.Panel("Playback Bar", canvas, 0, 700, 1400, 240);
            display = ui.Panel("Display", canvas, 160, 200, 1080, 430);
            subtitleSettings = ui.Panel("Subtitle Settings", canvas, 160, 115, 1080, 550);
            status = ui.Label(canvas, "", 20, 955, 1360, 100, 26);
            CreatePlaybackBar(); CreateDisplay(); CreateSubtitleSettings(); CreatePreviewCard();
            bar.gameObject.SetActive(false); display.gameObject.SetActive(false);
            subtitleSettings.gameObject.SetActive(false);
            PlaceUi();
        }
        void PlaceUi()
        {
            var yaw = Quaternion.Euler(0, viewer.transform.eulerAngles.y, 0);
            canvas.SetPositionAndRotation(viewer.transform.position +
                yaw * new Vector3(preferences.uiHorizontal, 0, 2.1f), yaw);
        }
        void Header(string heading, Action back)
        {
            SpatialUi.Clear(browser);
            ui.Label(browser, heading, 30, 16, 1050, 66, 32);
            ui.Button(browser, "Back", 1220, 24, 150, back);
        }
        void LoadServers()
        {
            bridge.BeginNavigation();
            status.text = bridge.Available ? "Loading saved sources..." : "Editor: use Open video to check the Unity rendering path.";
            if (!bridge.Available) { ShowServers(); return; }
            bridge.Request(new BridgeRequest { operation = "servers" }, response =>
            {
                if (!Accept(response)) return;
                servers = response.data.servers ?? Array.Empty<ServerItem>();
                status.text = ""; ShowServers();
            });
        }
        void ShowServers()
        {
            Header("XR Stream", LoadServers);
            ui.Button(browser, "Local folder", 30, 96, 240, () => bridge.ChooseLocalFolder(response =>
            { if (Accept(response) && !response.data.cancelled) LoadServers(); }));
            ui.Button(browser, "Add SMB server", 290, 96, 270, () => ServerForm(null));
#if UNITY_EDITOR
            ui.Button(browser, "Open video (Editor)", 580, 96, 350, OpenEditorVideo);
#endif
            var content = ui.Scroll(browser, 30, 180, 1340, 690, servers.Length, 90);
            for (var index = 0; index < servers.Length; index++)
            {
                var server = servers[index]; var y = index * 90;
                ui.Button(content, (server.local ? "Local: " : "SMB: ") + server.name, 0, y, 880, () =>
                { browsingServer = server; parents.Clear(); Browse(""); });
                if (!server.local) ui.Button(content, "Edit", 910, y, 180, () => ServerForm(server));
                ui.Button(content, "Remove", 1110, y, 210, () => ConfirmDelete(server));
            }
        }
        void ConfirmDelete(ServerItem server)
        {
            Header("Remove source: " + server.name, ShowServers);
            ui.Label(browser, "This removes the saved source. Its video files are kept.", 40, 160, 1280, 80);
            ui.Button(browser, "Remove source", 40, 280, 300, () => bridge.Request(
                new BridgeRequest { operation = "deleteServer", serverId = server.id }, response => { if (Accept(response)) LoadServers(); }));
        }
        void ServerForm(ServerItem server)
        {
            Header(server == null ? "Add SMB server" : "Edit SMB server", ShowServers);
            var name = ui.Field(browser, "Name", server?.name, 110);
            var address = ui.Field(browser, "Server address", server?.address, 175);
            var port = ui.Field(browser, "Port", (server?.port ?? 445).ToString(), 240);
            var share = ui.Field(browser, "Share (optional)", server?.share, 305);
            var username = ui.Field(browser, "Username", server?.username, 370);
            var password = ui.Field(browser, "Password", "", 435, true);
            var domain = ui.Field(browser, "Domain", server?.domain, 500);
            if (server != null) ui.Label(browser, "Leave password blank to keep the saved password.", 40, 565, 1270, 65);
            ui.Button(browser, "Test & save", 40, 665, 300, () =>
            {
                if (string.IsNullOrWhiteSpace(name.text) || string.IsNullOrWhiteSpace(address.text) ||
                    !int.TryParse(port.text, out var number) || number < 1 || number > 65535)
                { ShowError("Enter a name, server address and valid port."); return; }
                status.text = "Testing the SMB connection...";
                bridge.Request(new BridgeRequest { operation = "saveServer", network = true, serverId = server?.id ?? 0,
                    name = name.text.Trim(), address = address.text.Trim(), share = share.text.Trim(), port = number,
                    username = username.text, password = password.text, domain = domain.text }, response =>
                { if (Accept(response)) LoadServers(); });
            });
        }
        void Browse(string path)
        {
            bridge.BeginNavigation();
            folder = path; status.text = "Reading folder...";
            Header(browsingServer.name, BackFolder);
            bridge.Request(new BridgeRequest { operation = "browse", network = !browsingServer.local, serverId = browsingServer.id, path = path }, response =>
            {
                if (!Accept(response)) return;
                files = response.data.files ?? Array.Empty<FileItem>();
                status.text = ""; ShowFiles();
            });
        }
        void BackFolder()
        {
            if (parents.Count == 0) { browsingServer = null; LoadServers(); }
            else Browse(parents.Pop());
        }
        void ShowFiles()
        {
            Header(browsingServer.name, BackFolder);
            ui.Label(browser, string.IsNullOrEmpty(folder) ? "Root" : folder, 30, 92, 1340, 70, 22);
            var listed = files.Where(item => item.directory || item.video).ToArray();
            var content = ui.Scroll(browser, 30, 180, 1340, 690, listed.Length, 84);
            for (var index = 0; index < listed.Length; index++)
            {
                var item = listed[index];
                ui.Button(content, (item.directory ? "[Folder] " : "") + item.name, 0, index * 84, 1310, () =>
                {
                    if (item.directory) { parents.Push(folder); Browse(item.path); }
                    else { queue = listed.Where(entry => entry.video && !entry.directory).ToArray(); Open(item); }
                });
            }
        }
        void Open(FileItem item)
        {
            CloseVideo(false); bridge.BeginNavigation();
            controls.Reset(Time.unscaledTimeAsDouble);
            current = item; playingServer = browsingServer;
            browser.gameObject.SetActive(false); bar.gameObject.SetActive(true); display.gameObject.SetActive(false);
            title.text = item.name; status.text = "Opening video..."; SetPlaybackEnabled(false);
            RuntimeDiagnostics.Log("source-open", $"source={(playingServer.local ? "local" : "smb")}");
            bridge.Request(new BridgeRequest { operation = "open", network = !playingServer.local, serverId = playingServer.id, path = item.path }, response =>
            {
                if (!Accept(response)) return;
                token = response.data.token;
                format = Enum.TryParse(response.data.format, out VideoFormat savedFormat) ? savedFormat : VideoFormat.Format2D;
                stereo = Enum.TryParse(response.data.stereo, out StereoMode savedStereo) ? savedStereo : StereoMode.Mono;
                autoInfer = format == VideoFormat.Format2D && stereo == StereoMode.Mono;
                playback.Open(response.data.url, response.data.positionMs / 1000d);
                LoadSubtitle(item);
            });
        }
        void LoadSubtitle(FileItem item)
        {
            if (string.IsNullOrEmpty(item.subtitlePath)) return;
            bridge.Request(new BridgeRequest { operation = "subtitle", network = !playingServer.local,
                serverId = playingServer.id, path = item.subtitlePath }, response =>
            {
                if (!response.success) { ShowError("Subtitle: " + response.error); return; }
                subtitles.SetFrames(response.data.subtitleFrames);
            });
        }
#if UNITY_EDITOR
        void OpenEditorVideo()
        {
            var path = UnityEditor.EditorUtility.OpenFilePanel("Select a real video", "", "");
            if (string.IsNullOrEmpty(path)) return;
            CloseVideo(false); bridge.BeginNavigation();
            current = new FileItem { name = System.IO.Path.GetFileName(path), path = path, video = true };
            playingServer = null; queue = Array.Empty<FileItem>();
            format = VideoFormat.Format2D; stereo = StereoMode.Mono; autoInfer = true;
            browser.gameObject.SetActive(false); bar.gameObject.SetActive(true);
            title.text = current.name; status.text = "Opening video..."; SetPlaybackEnabled(false);
            playback.Open(new Uri(path).AbsoluteUri, 0);
            controls.Reset(Time.unscaledTimeAsDouble);
        }
#endif
        void VideoReady()
        {
            if (autoInfer && playback.Texture.width >= 4096 && playback.Texture.height >= 2048 &&
                Mathf.Abs((float)playback.Texture.width / playback.Texture.height - 2) <= .05f)
            { format = VideoFormat.Format180; stereo = StereoMode.SideBySide; }
            projection.Configure(format, stereo, playback.Texture);
            timeline.maxValue = (float)playback.Duration;
            totalTime.text = TimeLabel(playback.Duration);
            status.text = ""; lastSave = Time.unscaledTime;
            controls.Show(Time.unscaledTimeAsDouble);
            SetPlaybackEnabled(true);
        }
        void VideoEnded()
        {
            SavePlayback();
            MoveQueue(1);
        }
        void PlaybackError(string error)
        {
            EndPreview();
            controls.Show(Time.unscaledTimeAsDouble); ApplyControlsVisibility();
            projection.Hide(); bridge.ReleaseStream(token); token = null;
            SetPlaybackEnabled(false); ShowError(error);
        }
        void CreatePlaybackBar()
        {
            title = ui.Label(bar, "", 25, 5, 1340, 45, 23);
            currentTime = ui.Label(bar, "00:00", 25, 62, 150, 52, 22);
            timeline = (PlaybackTimeline)ui.Slider(bar, "Timeline", 180, 62, 1010, 1, value =>
            {
                if (timeline != null && timeline.Dragging)
                { currentTime.text = TimeLabel(value); UpdatePreviewPosition(value); previewPlayer?.Request(value); }
            }, true);
            timeline.BeginSeek += () =>
            {
                wasPlayingBeforeDrag = playback.WantsPlayback; playback.Pause();
                display.gameObject.SetActive(false); subtitleSettings.gameObject.SetActive(false);
                previewCard.gameObject.SetActive(true); previewImage.texture = null; previewMessage.text = "Loading preview...";
                UpdatePreviewPosition(timeline.value);
                previewPlayer.Begin(playback.SourceUrl, timeline.value, playback.Duration);
            };
            timeline.CommitSeek += () => { EndPreview(); playback.Seek(timeline.value, wasPlayingBeforeDrag); };
            timeline.CancelSeek += () => { EndPreview(); if (wasPlayingBeforeDrag) playback.Play(); };
            BindControlsInteraction(timeline);
            totalTime = ui.Label(bar, "00:00", 1220, 62, 160, 52, 22);
            ui.Button(bar, "Prev", 25, 145, 100, () => MoveQueue(-1));
            ui.Button(bar, "-10s", 135, 145, 100, () => playback.Seek(playback.DisplayPosition - 10, playback.WantsPlayback));
            playButton = ui.Button(bar, "Play", 245, 145, 130, () =>
            { if (playback.WantsPlayback) { playback.Pause(); SavePlayback(); } else playback.Play(); });
            playLabel = playButton.GetComponentInChildren<Text>();
            ui.Button(bar, "+10s", 385, 145, 100, () => playback.Seek(playback.DisplayPosition + 10, playback.WantsPlayback));
            ui.Button(bar, "Next", 495, 145, 100, () => MoveQueue(1));
            ui.Label(bar, "Vol", 615, 145, 60, 58, 22);
            var volume = ui.Slider(bar, "Volume", 680, 149, 160, 1, value => playback.Volume = value);
            BindControlsInteraction((PlaybackTimeline)volume);
            volume.SetValueWithoutNotify(1);
            ui.Button(bar, "Display", 865, 145, 160, () =>
            { subtitleSettings.gameObject.SetActive(false); display.gameObject.SetActive(!display.gameObject.activeSelf); });
            var recenterButton = ui.Button(bar, "Recenter", 1035, 145, 185, StartRecenter);
            recenterLabel = recenterButton.GetComponentInChildren<Text>();
            ui.Button(bar, "Exit", 1230, 145, 140, () => CloseVideo(true));
        }
        void CreateDisplay()
        {
            ui.Label(display, "Display", 25, 15, 800, 60, 32);
            ui.Button(display, "Close", 895, 20, 150, () => display.gameObject.SetActive(false));
            ui.Label(display, "Projection", 25, 90, 200, 58);
            ui.Button(display, "2D", 240, 90, 240, () => ChangeDisplay(VideoFormat.Format2D, stereo));
            ui.Button(display, "VR180", 505, 90, 240, () => ChangeDisplay(VideoFormat.Format180, stereo));
            ui.Button(display, "VR360", 770, 90, 240, () => ChangeDisplay(VideoFormat.Format360, stereo));
            ui.Label(display, "Stereo", 25, 180, 200, 58);
            ui.Button(display, "Mono", 240, 180, 240, () => ChangeDisplay(format, StereoMode.Mono));
            ui.Button(display, "SBS", 505, 180, 240, () => ChangeDisplay(format, StereoMode.SideBySide));
            ui.Button(display, "Top / Bottom", 770, 180, 240, () => ChangeDisplay(format, StereoMode.TopBottom));
            ui.Label(display, "Speed", 25, 285, 170, 58);
            foreach (var rate in new[] { .5f, 1f, 1.5f, 2f })
            {
                var offset = Array.IndexOf(new[] { .5f, 1f, 1.5f, 2f }, rate);
                ui.Button(display, rate + "x", 210 + offset * 205, 285, 180, () => playback.Speed = rate);
            }
            audioButton = ui.Button(display, "Audio", 25, 365, 480, () => playback.NextAudioTrack());
            audioLabel = audioButton.GetComponentInChildren<Text>();
            subtitleButton = ui.Button(display, "Subtitles", 530, 365, 250, () => subtitles.Toggle());
            subtitleLabel = subtitleButton.GetComponentInChildren<Text>();
            ui.Button(display, "Subtitle settings", 790, 365, 250, () =>
            { display.gameObject.SetActive(false); subtitleSettings.gameObject.SetActive(true); });
        }
        void CreatePreviewCard()
        {
            previewCard = ui.Panel("Seek Preview", canvas, 480, 400, 440, 288);
            previewCard.GetComponent<Image>().raycastTarget = false;
            previewImage = ui.Rect("Preview Image", previewCard, 15, 12, 410, 230).gameObject.AddComponent<RawImage>();
            previewImage.raycastTarget = false;
            previewMessage = ui.Label(previewCard, "", 15, 12, 410, 230, 23);
            previewMessage.alignment = TextAnchor.MiddleCenter;
            previewTime = ui.Label(previewCard, "", 15, 242, 410, 40, 23);
            previewTime.alignment = TextAnchor.MiddleCenter;
            previewCard.gameObject.SetActive(false);
        }
        void UpdatePreviewPosition(double target)
        {
            previewTime.text = TimeLabel(target);
            var ratio = playback.Duration > 0 ? target / playback.Duration : 0;
            previewCard.anchoredPosition = new Vector2(Mathf.Clamp(180 + (float)ratio * 1010 - 220, 25, 935), -400);
        }
        void PreviewReady(RenderTexture frame)
        {
            if (!timeline.Dragging) return;
            previewImage.texture = frame; previewMessage.text = "";
            var aspect = (float)frame.width / frame.height;
            var width = Mathf.Min(410, 230 * aspect); var height = width / aspect;
            previewImage.rectTransform.sizeDelta = new Vector2(width, height);
            previewImage.rectTransform.anchoredPosition = new Vector2(15 + (410 - width) / 2, -12 - (230 - height) / 2);
        }
        void EndPreview()
        {
            if (previewCard == null) return;
            previewCard.gameObject.SetActive(false); previewImage.texture = null; previewPlayer?.End();
        }
        void CreateSubtitleSettings()
        {
            ui.Label(subtitleSettings, "Subtitle settings", 25, 15, 800, 60, 32);
            ui.Button(subtitleSettings, "Close", 895, 20, 150, () =>
            { subtitleSettings.gameObject.SetActive(false); FlushPreferences(); display.gameObject.SetActive(true); });
            subtitleSizeLabel = ui.Label(subtitleSettings, "", 25, 100, 310, 58, 22);
            ui.Button(subtitleSettings, "Change size", 355, 100, 300, () =>
            {
                var index = Array.IndexOf(PlaybackPreferences.Sizes, preferences.subtitleSize);
                preferences.subtitleSize = PlaybackPreferences.Sizes[(index + 1) % PlaybackPreferences.Sizes.Length];
                PreferencesChanged();
            });
            subtitleDistanceSlider = PreferenceSlider("Distance", 185, preferences.minDistance, preferences.maxDistance,
                value => { preferences.subtitleDistance = value; PreferencesChanged(); });
            subtitleDistanceLabel = ui.Label(subtitleSettings, "", 895, 185, 150, 58, 22);
            subtitleVerticalSlider = PreferenceSlider("Vertical", 270, preferences.minVertical, preferences.maxVertical,
                value => { preferences.subtitleVertical = value; PreferencesChanged(); });
            subtitleVerticalLabel = ui.Label(subtitleSettings, "", 895, 270, 150, 58, 22);
            uiHorizontalSlider = PreferenceSlider("UI horizontal", 355, preferences.minHorizontal, preferences.maxHorizontal,
                value => { preferences.uiHorizontal = value; PreferencesChanged(); });
            uiHorizontalLabel = ui.Label(subtitleSettings, "", 895, 355, 150, 58, 22);
            ui.Button(subtitleSettings, "Reset subtitle settings", 25, 450, 490, () =>
            { preferences.ResetSubtitles(); RefreshPreferenceControls(); PreferencesChanged(); });
            RefreshPreferenceControls();
        }
        Slider PreferenceSlider(string name, float y, float minimum, float maximum, Action<float> changed)
        {
            ui.Label(subtitleSettings, name, 25, y, 310, 58, 22);
            var slider = (PlaybackTimeline)ui.Slider(subtitleSettings, name, 355, y + 3, 500, maximum, changed, true, minimum);
            slider.BeginSeek += () => settingsInputActive = true;
            slider.CommitSeek += FinishSettingsInput;
            slider.CancelSeek += FinishSettingsInput;
            BindControlsInteraction(slider);
            return slider;
        }
        void BindControlsInteraction(PlaybackTimeline slider)
        {
            slider.BeginSeek += () => controls.BeginInteraction(Time.unscaledTimeAsDouble);
            slider.CommitSeek += () => controls.EndInteraction(Time.unscaledTimeAsDouble);
            slider.CancelSeek += () => controls.EndInteraction(Time.unscaledTimeAsDouble);
        }
        bool PointerPressed(bool uiHit)
        {
            if (current == null || !playback.Prepared) return false;
            var now = Time.unscaledTimeAsDouble;
            if (uiHit && controls.Visible) { controls.Touch(now); return false; }
            if (!controls.Toggle(now, playback.Seeking)) return true;
            if (controls.Visible) PlaceUi();
            else { display.gameObject.SetActive(false); subtitleSettings.gameObject.SetActive(false); }
            ApplyControlsVisibility();
            return true;
        }
        void ApplyControlsVisibility()
        {
            bar.gameObject.SetActive(controls.Visible);
            RuntimeDiagnostics.Log("controls-visible", $"visible={controls.Visible} locks={controls.InteractionLocks}");
        }
        void FinishSettingsInput() { settingsInputActive = false; PlaceUi(); FlushPreferences(); }
        void RefreshPreferenceControls()
        {
            subtitleDistanceSlider.SetValueWithoutNotify(preferences.subtitleDistance);
            subtitleVerticalSlider.SetValueWithoutNotify(preferences.subtitleVertical);
            uiHorizontalSlider.SetValueWithoutNotify(preferences.uiHorizontal);
            RefreshPreferenceLabels();
        }
        void RefreshPreferenceLabels()
        {
            subtitleSizeLabel.text = "Size: " + preferences.subtitleSize;
            subtitleDistanceLabel.text = $"{preferences.subtitleDistance:F1} m";
            subtitleVerticalLabel.text = $"{preferences.subtitleVertical:F2} m";
            uiHorizontalLabel.text = $"{preferences.uiHorizontal:F2} m";
        }
        void PreferencesChanged()
        {
            preferences.Normalize(); subtitles?.ApplyPreferences(preferences);
            RefreshPreferenceLabels();
            if (!settingsInputActive) PlaceUi();
            preferencesDirty = true; preferencesSaveAt = Time.unscaledTime + .3f;
        }
        void FlushPreferences()
        {
            if (!preferencesDirty || bridge == null) return;
            bridge.SavePlaybackPreferences(preferences); preferencesDirty = false;
        }
        void ChangeDisplay(VideoFormat nextFormat, StereoMode nextStereo)
        {
            format = nextFormat; stereo = nextStereo; autoInfer = false;
            if (playback.Prepared) { projection.Configure(format, stereo, playback.Texture); SavePlayback(); }
        }
        void StartRecenter()
        {
            if (recenter != null) { StopCoroutine(recenter); recenter = null; recenterLabel.text = "Recenter"; return; }
            recenter = StartCoroutine(RecenterCountdown());
        }
        IEnumerator RecenterCountdown()
        {
            for (var remaining = 3; remaining > 0; remaining--)
            { recenterLabel.text = "Recenter " + remaining; yield return new WaitForSecondsRealtime(1); }
            preferences.uiHorizontal = 0; RefreshPreferenceControls(); PreferencesChanged(); FlushPreferences();
            projection.Recenter(); PlaceUi(); recenterLabel.text = "Recenter"; recenter = null;
        }
        void MoveQueue(int direction)
        {
            if (current == null || queue.Length == 0) return;
            var next = Array.FindIndex(queue, item => item.path == current.path) + direction;
            if (next >= 0 && next < queue.Length) Open(queue[next]);
        }
        void SetPlaybackEnabled(bool enabled)
        {
            timeline.interactable = enabled; playButton.interactable = enabled;
        }
        void SavePlayback()
        {
            if (current == null || playingServer == null || !playback.Prepared || playback.Seeking) return;
            bridge.Request(new BridgeRequest { operation = "savePlayback", serverId = playingServer.id,
                path = current.path, name = current.name, positionMs = (long)(playback.Position * 1000),
                durationMs = (long)(playback.Duration * 1000), format = format.ToString(), stereo = stereo.ToString() }, null);
        }
        void CloseVideo(bool restoreBrowser)
        {
            SavePlayback();
            EndPreview();
            if (recenter != null) { StopCoroutine(recenter); recenter = null; recenterLabel.text = "Recenter"; }
            bar.gameObject.SetActive(false); display.gameObject.SetActive(false); subtitleSettings.gameObject.SetActive(false);
            FlushPreferences();
            playback.Stop(); projection.Hide(); bridge.ReleaseStream(token);
            token = null; current = null; playingServer = null;
            subtitles?.Clear();
            if (restoreBrowser) { bridge.BeginNavigation(); browser.gameObject.SetActive(true); status.text = ""; }
        }
        bool Accept(BridgeResponse response)
        {
            if (response.success) return true;
            ShowError(response.error); return false;
        }
        void ShowError(string error) { status.text = error; Debug.LogError("XRStream: " + error); }
        void Update()
        {
            if (preferencesDirty && Time.unscaledTime >= preferencesSaveAt) FlushPreferences();
            if (playback != null && subtitles != null)
            {
                audioLabel.text = playback.AudioTrackLabel;
                audioButton.interactable = playback.AudioTrackCount > 1 && !playback.Seeking;
                subtitleLabel.text = subtitles.Available ? "Subtitles: " + (subtitles.EnabledByUser ? "On" : "Off") : "Subtitles: none";
                subtitleButton.interactable = subtitles.Available;
            }
            if (playback == null || !playback.Prepared) return;
            var menuOpen = display.gameObject.activeSelf || subtitleSettings.gameObject.activeSelf || recenter != null;
            if (controls.Tick(Time.unscaledTimeAsDouble, playback.WantsPlayback, menuOpen, playback.Seeking))
                ApplyControlsVisibility();
#if UNITY_EDITOR
            if (Mouse.current?.leftButton.wasPressedThisFrame == true)
                PointerPressed(EventSystem.current != null && EventSystem.current.IsPointerOverGameObject());
#endif
            if (!timeline.Dragging)
            { timeline.SetValueWithoutNotify((float)playback.DisplayPosition); currentTime.text = TimeLabel(playback.DisplayPosition); }
            playLabel.text = playback.WantsPlayback ? "Pause" : "Play";
            if (Time.unscaledTime - lastSave >= 5) { SavePlayback(); lastSave = Time.unscaledTime; }
        }
        void OnApplicationPause(bool paused)
        {
            RuntimeDiagnostics.Log("app-pause", $"paused={paused}");
            if (playback == null) return;
            if (paused)
            {
                appPauseWasPlaying = timeline.Dragging ? wasPlayingBeforeDrag : playback.WantsPlayback;
                foreach (var slider in canvas.GetComponentsInChildren<PlaybackTimeline>(true)) slider.CancelInteraction();
                controls.Reset(Time.unscaledTimeAsDouble);
                if (current != null) ApplyControlsVisibility();
                FlushPreferences(); SavePlayback(); playback.Pause();
            }
            else if (appPauseWasPlaying) { playback.Play(); appPauseWasPlaying = false; }
        }
        void OnApplicationQuit() { FlushPreferences(); if (playback != null) SavePlayback(); }
        void OnDisable()
        {
            if (playback != null && bridge != null && bar != null) CloseVideo(false);
        }
        static string TimeLabel(double seconds)
        {
            var span = TimeSpan.FromSeconds(Math.Max(0, double.IsFinite(seconds) ? seconds : 0));
            return span.TotalHours >= 1 ? $"{(int)span.TotalHours}:{span.Minutes:00}:{span.Seconds:00}" : $"{(int)span.TotalMinutes:00}:{span.Seconds:00}";
        }
    }
}
