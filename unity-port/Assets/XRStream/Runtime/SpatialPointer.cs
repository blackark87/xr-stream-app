using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.XR.Interaction.Toolkit.Interactors;
using UnityEngine.XR.Interaction.Toolkit.Inputs.Readers;

namespace XRStream
{
    public sealed class SpatialPointer : MonoBehaviour
    {
        InputAction position, rotation, tracked, pressed, value;
        XRRayInteractor interactor;
        LineRenderer line;
        Material lineMaterial;
        Transform trackingOrigin;
        bool? wasActive;
        public event System.Func<bool, bool> Pressed;
        bool suppressUntilRelease;
        public void Initialize(Transform origin, string hand, bool handTracking)
        {
            trackingOrigin = origin;
            var source = (handTracking ? "<HandInteraction>" : "<XRController>") + "{" + hand + "}";
            position = new InputAction("AimPosition", InputActionType.Value, source + "/{Pointer}/position");
            rotation = new InputAction("AimRotation", InputActionType.Value, source + "/{Pointer}/rotation");
            tracked = new InputAction("Tracked", InputActionType.Value, source + "/isTracked");
            pressed = new InputAction("UI Press", InputActionType.Button, source + (handTracking ? "/{PointerActivated}" : "/triggerPressed"));
            value = new InputAction("UI Press Value", InputActionType.Value, source + (handTracking ? "/{PointerActivateValue}" : "/trigger"));
            interactor = gameObject.AddComponent<XRRayInteractor>();
            interactor.enableUIInteraction = true;
            interactor.maxRaycastDistance = 20;
            interactor.uiPressInput = new XRInputButtonReader("UI Press", inputSourceMode: XRInputButtonReader.InputSourceMode.ManualValue);
            line = gameObject.AddComponent<LineRenderer>();
            line.positionCount = 2; line.startWidth = .002f; line.endWidth = .002f;
            lineMaterial = new Material(Shader.Find("XRStream/StereoVideo"));
            lineMaterial.mainTexture = Texture2D.whiteTexture;
            lineMaterial.SetColor("_Color", new Color(.2f, .8f, 1));
            line.sharedMaterial = lineMaterial;
            position.Enable(); rotation.Enable(); tracked.Enable(); pressed.Enable(); value.Enable();
        }
        void Update()
        {
            if (tracked == null) return;
            var active = tracked.ReadValue<float>() > .5f && pressed.controls.Count > 0;
            if (wasActive != active)
            {
                RuntimeDiagnostics.Log("input-tracking", $"pointer={name} active={active} poseBindings={position.controls.Count} pressBindings={pressed.controls.Count}");
                wasActive = active;
            }
            interactor.enabled = active;
            line.enabled = active;
            if (!active)
            {
                interactor.uiPressInput.QueueManualState(false, 0);
                suppressUntilRelease = false; return;
            }
            if (!pressed.IsPressed()) suppressUntilRelease = false;
            transform.SetPositionAndRotation(trackingOrigin.TransformPoint(position.ReadValue<Vector3>()), trackingOrigin.rotation * rotation.ReadValue<Quaternion>());
            var end = transform.position + transform.forward * 3;
            if (interactor.TryGetCurrentUIRaycastResult(out var hit)) end = hit.worldPosition;
            if (pressed.WasPressedThisFrame())
            {
                RuntimeDiagnostics.Log("input-press", $"pointer={name} uiHit={hit.gameObject != null}");
                if (Pressed?.Invoke(hit.gameObject != null) == true) suppressUntilRelease = true;
            }
            // Reveal/hide presses are consumed until release, never activating a newly shown button.
            interactor.uiPressInput.QueueManualState(!suppressUntilRelease && pressed.IsPressed(),
                suppressUntilRelease ? 0 : value.ReadValue<float>());
            line.SetPosition(0, transform.position); line.SetPosition(1, end);
        }
        void OnDestroy()
        {
            position?.Dispose(); rotation?.Dispose(); tracked?.Dispose(); pressed?.Dispose(); value?.Dispose();
            if (lineMaterial != null) Destroy(lineMaterial);
        }
    }
}
