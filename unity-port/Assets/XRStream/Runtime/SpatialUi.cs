using System;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace XRStream
{
    // Pixel coordinates describe a world-space canvas; the canvas controls physical size.
    public sealed class SpatialUi
    {
        readonly Font font = Resources.Load<Font>("Fonts/NotoSansKR-Regular") ??
            Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
        public readonly Color Accent = new(.14f, .42f, .67f, 1);
        public RectTransform Rect(string name, Transform parent, float x, float y, float width, float height)
        {
            var rect = new GameObject(name, typeof(RectTransform)).GetComponent<RectTransform>();
            rect.SetParent(parent, false);
            rect.anchorMin = rect.anchorMax = rect.pivot = new Vector2(0, 1);
            rect.anchoredPosition = new Vector2(x, -y);
            rect.sizeDelta = new Vector2(width, height);
            return rect;
        }
        public RectTransform Panel(string name, Transform parent, float x, float y, float width, float height)
        {
            var rect = Rect(name, parent, x, y, width, height);
            rect.gameObject.AddComponent<Image>().color = new Color(.035f, .045f, .07f, .96f);
            return rect;
        }
        public Text Label(Transform parent, string text, float x, float y, float width, float height, int size = 26)
        {
            var label = Rect(text, parent, x, y, width, height).gameObject.AddComponent<Text>();
            label.font = font; label.fontSize = size; label.color = Color.white;
            label.text = text; label.alignment = TextAnchor.MiddleLeft; label.supportRichText = false;
            label.raycastTarget = false;
            return label;
        }
        public Button Button(Transform parent, string text, float x, float y, float width, Action action)
        {
            var rect = Rect(text, parent, x, y, width, 58);
            var image = rect.gameObject.AddComponent<Image>(); image.color = Accent;
            var button = rect.gameObject.AddComponent<Button>(); button.targetGraphic = image;
            var colors = button.colors;
            colors.highlightedColor = new Color(.6f, .85f, 1); colors.pressedColor = new Color(.35f, .6f, .8f);
            button.colors = colors;
            var label = Label(rect, text, 6, 0, width - 12, 58);
            label.alignment = TextAnchor.MiddleCenter;
            button.onClick.AddListener(() => action());
            return button;
        }
        public InputField Field(Transform parent, string name, string value, float y, bool password = false)
        {
            Label(parent, name, 40, y, 240, 50);
            var rect = Rect(name + " Input", parent, 290, y, 1020, 50);
            var image = rect.gameObject.AddComponent<Image>(); image.color = new Color(.1f, .15f, .21f);
            var field = rect.gameObject.AddComponent<InputField>(); field.targetGraphic = image;
            field.textComponent = Label(rect, "", 12, 0, 996, 50);
            field.textComponent.supportRichText = false;
            field.contentType = password ? InputField.ContentType.Password : InputField.ContentType.Standard;
            field.text = value ?? "";
            return field;
        }
        public Slider Slider(Transform parent, string name, float x, float y, float width, float maximum, Action<float> changed, bool seek = false, float minimum = 0)
        {
            var rect = Rect(name, parent, x, y, width, 52);
            var rail = Rect("Rail", rect, 0, 17, width, 18);
            rail.gameObject.AddComponent<Image>().color = new Color(.18f, .25f, .34f);
            var handle = Rect("Handle", rect, 0, 7, 32, 38);
            handle.gameObject.AddComponent<Image>().color = Color.white;
            // Every slider reports interaction lifetime so controls cannot hide during a drag.
            var slider = rect.gameObject.AddComponent<PlaybackTimeline>();
            slider.handleRect = handle; slider.targetGraphic = handle.GetComponent<Image>();
            slider.minValue = minimum; slider.maxValue = maximum;
            slider.onValueChanged.AddListener(value => changed(value));
            return slider;
        }
        public RectTransform Scroll(Transform parent, float x, float y, float width, float height, int count, float rowHeight)
        {
            var area = Rect("Scroll", parent, x, y, width, height);
            var image = area.gameObject.AddComponent<Image>(); image.color = new Color(0, 0, 0, .08f);
            area.gameObject.AddComponent<RectMask2D>();
            var scroll = area.gameObject.AddComponent<ScrollRect>();
            var content = Rect("Content", area, 0, 0, width - 8, Mathf.Max(height, count * rowHeight));
            scroll.viewport = area; scroll.content = content; scroll.horizontal = false;
            scroll.movementType = ScrollRect.MovementType.Clamped;
            return content;
        }
        public static void Clear(Transform parent)
        {
            for (var i = parent.childCount - 1; i >= 0; i--)
            {
                var child = parent.GetChild(i).gameObject;
                child.SetActive(false); UnityEngine.Object.Destroy(child);
            }
        }
    }
    public sealed class PlaybackTimeline : Slider
    {
        public event Action BeginSeek, CommitSeek, CancelSeek;
        public bool Dragging { get; private set; }
        public override void OnPointerDown(PointerEventData data)
        {
            if (!IsActive() || !IsInteractable()) return;
            Dragging = true; BeginSeek?.Invoke();
            base.OnPointerDown(data);
        }
        public override void OnPointerUp(PointerEventData data)
        {
            base.OnPointerUp(data);
            if (!Dragging) return;
            Dragging = false; CommitSeek?.Invoke();
        }
        protected override void OnDisable()
        {
            CancelInteraction();
            base.OnDisable();
        }
        public void CancelInteraction()
        {
            if (!Dragging) return;
            Dragging = false; CancelSeek?.Invoke();
        }
    }
}
