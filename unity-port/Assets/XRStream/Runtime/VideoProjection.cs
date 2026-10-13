using UnityEngine;

namespace XRStream
{
    public sealed class VideoProjection : MonoBehaviour
    {
        MeshFilter filter;
        MeshRenderer renderer;
        Material material;
        Mesh ownedMesh;
        Camera viewer;
        public VideoFormat Format { get; private set; }
        public StereoMode Stereo { get; private set; }

        public void Initialize(Camera camera)
        {
            viewer = camera;
            filter = gameObject.AddComponent<MeshFilter>();
            renderer = gameObject.AddComponent<MeshRenderer>();
            var shader = Shader.Find("XRStream/StereoVideo");
            if (shader == null) throw new System.InvalidOperationException("XRStream stereo shader is missing.");
            material = new Material(shader);
            renderer.sharedMaterial = material;
            renderer.enabled = false;
        }
        public void Configure(VideoFormat format, StereoMode stereo, Texture texture)
        {
            Format = format;
            Stereo = stereo;
            if (ownedMesh != null) Destroy(ownedMesh);
            ownedMesh = format == VideoFormat.Format2D ? Quad() : Panorama(format == VideoFormat.Format180 ? 180 : 360);
            filter.sharedMesh = ownedMesh;
            material.SetFloat("_StereoMode", (int)stereo);
            material.mainTexture = texture;
            renderer.enabled = texture != null;
            RuntimeDiagnostics.Log("projection", $"format={format} stereo={stereo} texture={texture?.width}x{texture?.height}");
            if (format == VideoFormat.Format2D && texture != null)
            {
                var aspect = (float)texture.width / texture.height;
                if (stereo == StereoMode.SideBySide) aspect *= .5f;
                if (stereo == StereoMode.TopBottom) aspect *= 2;
                transform.localScale = new Vector3(3.2f, 3.2f / aspect, 1);
            }
            else transform.localScale = Vector3.one;
            Recenter();
        }
        public void Hide() { renderer.enabled = false; material.mainTexture = null; }
        public void Recenter()
        {
            var yaw = Quaternion.Euler(0, viewer.transform.eulerAngles.y, 0);
            transform.SetPositionAndRotation(viewer.transform.position + (Format == VideoFormat.Format2D ? yaw * Vector3.forward * 3.5f : Vector3.zero), yaw);
        }
        void LateUpdate()
        {
            if (viewer != null && Format != VideoFormat.Format2D) transform.position = viewer.transform.position;
        }
        static Mesh Quad() => new Mesh
        {
            vertices = new[] { new Vector3(-.5f, -.5f, 0), new Vector3(.5f, -.5f, 0), new Vector3(-.5f, .5f, 0), new Vector3(.5f, .5f, 0) },
            uv = new[] { Vector2.zero, Vector2.right, Vector2.up, Vector2.one },
            triangles = new[] { 0, 2, 1, 1, 2, 3 },
        };
        static Mesh Panorama(int degrees)
        {
            const int horizontal = 96, vertical = 48;
            var vertices = new Vector3[(horizontal + 1) * (vertical + 1)];
            var uv = new Vector2[vertices.Length];
            var triangles = new int[horizontal * vertical * 6];
            var index = 0;
            for (var y = 0; y <= vertical; y++)
            for (var x = 0; x <= horizontal; x++)
            {
                var u = (float)x / horizontal;
                var v = (float)y / vertical;
                var longitude = (u - .5f) * degrees * Mathf.Deg2Rad;
                var latitude = (v - .5f) * Mathf.PI;
                var at = y * (horizontal + 1) + x;
                vertices[at] = 12 * new Vector3(Mathf.Sin(longitude) * Mathf.Cos(latitude), Mathf.Sin(latitude), Mathf.Cos(longitude) * Mathf.Cos(latitude));
                uv[at] = new Vector2(u, v);
                if (x == horizontal || y == vertical) continue;
                triangles[index++] = at; triangles[index++] = at + horizontal + 1; triangles[index++] = at + 1;
                triangles[index++] = at + 1; triangles[index++] = at + horizontal + 1; triangles[index++] = at + horizontal + 2;
            }
            var mesh = new Mesh { name = "XRStream Panorama", vertices = vertices, uv = uv, triangles = triangles };
            mesh.RecalculateBounds();
            return mesh;
        }
        void OnDestroy()
        {
            if (ownedMesh != null) Destroy(ownedMesh);
            if (material != null) Destroy(material);
        }
    }
}
