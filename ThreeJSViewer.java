import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Affine;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.PerspectiveCamera;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class ThreeJSViewer extends Application {

    private Group root3D = new Group();

    @Override
    public void start(Stage stage) {
        Button loadButton = new Button("Load 3D Model");
        loadButton.setOnAction(e -> loadModel(stage));

        SubScene subScene = new SubScene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.GRAY);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-10);
        subScene.setCamera(camera);

        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(5);
        light.setTranslateY(-5);
        light.setTranslateZ(-5);
        root3D.getChildren().add(light);

        StackPane pane = new StackPane(subScene, loadButton);
        Scene scene = new Scene(pane, 800, 600);
        stage.setScene(scene);
        stage.setTitle("3D JSON Model Viewer");
        stage.show();
    }

    private void loadModel(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = fileChooser.showOpenDialog(stage);
        if (file == null) return;

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray geometries = json.getAsJsonArray("geometries");
            JsonArray materials = json.getAsJsonArray("materials");
            JsonObject object = json.getAsJsonObject("object");

            Map<String, Shape3D> geoMap = parseGeometries(geometries);
            Map<String, PhongMaterial> matMap = parseMaterials(materials);

            Node model = buildObject(object, geoMap, matMap);
            if (model != null) {
                root3D.getChildren().add(model);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Map<String, Shape3D> parseGeometries(JsonArray geometries) {
        Map<String, Shape3D> geoMap = new HashMap<>();
        for (JsonElement g : geometries) {
            JsonObject go = g.getAsJsonObject();
            String uuid = go.get("uuid").getAsString();
            String type = go.get("type").getAsString();

            switch (type) {
                case "SphereGeometry":
                    double radius = go.get("radius").getAsDouble();
                    int widthSegments = go.get("widthSegments").getAsInt();
                    Sphere sphere = new Sphere(radius, widthSegments);
                    geoMap.put(uuid, sphere);
                    break;
                case "CylinderGeometry":
                    double radiusTop = go.get("radiusTop").getAsDouble();
                    double radiusBottom = go.get("radiusBottom").getAsDouble();
                    double height = go.get("height").getAsDouble();
                    int radialSegments = go.get("radialSegments").getAsInt();
                    if (radiusTop == radiusBottom) {
                        Cylinder cylinder = new Cylinder(radiusTop, height, radialSegments);
                        cylinder.setDrawMode(DrawMode.FILL);
                        geoMap.put(uuid, cylinder);
                    } else {
                        // Treat as cone if one radius is 0
                        double coneRadius = (radiusTop == 0) ? radiusBottom : radiusTop;
                        boolean inverted = (radiusTop == 0);
                        MeshView cone = createCone(coneRadius, height, radialSegments);
                        if (inverted) {
                            // Flip if top is 0
                            cone.getTransforms().add(new Affine(1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0));
                        }
                        geoMap.put(uuid, cone);
                    }
                    break;
                case "BoxGeometry":
                    double width = go.get("width").getAsDouble();
                    double h = go.get("height").getAsDouble();
                    double depth = go.get("depth").getAsDouble();
                    geoMap.put(uuid, new Box(width, h, depth));
                    break;
                case "ConeGeometry":
                    double coneR = go.get("radius").getAsDouble();
                    double coneH = go.get("height").getAsDouble();
                    int coneSegments = go.get("radialSegments").getAsInt();
                    geoMap.put(uuid, createCone(coneR, coneH, coneSegments));
                    break;
                case "PlaneGeometry":
                    double pWidth = go.get("width").getAsDouble();
                    double pHeight = go.get("height").getAsDouble();
                    geoMap.put(uuid, createPlane(pWidth, pHeight));
                    break;
                // Add more types as needed
            }
        }
        return geoMap;
    }

    private Map<String, PhongMaterial> parseMaterials(JsonArray materials) {
        Map<String, PhongMaterial> matMap = new HashMap<>();
        for (JsonElement m : materials) {
            JsonObject mo = m.getAsJsonObject();
            String uuid = mo.get("uuid").getAsString();
            int colorInt = mo.get("color").getAsInt();
            double roughness = mo.get("roughness").getAsDouble();
            double metalness = mo.get("metalness").getAsDouble();

            Color color = intToColor(colorInt);
            PhongMaterial mat = new PhongMaterial(color);
            mat.setSpecularColor(metalness > 0 ? Color.WHITE : Color.BLACK); // Approximate metalness
            mat.setSpecularPower(10 * (1 - roughness)); // Approximate roughness
            matMap.put(uuid, mat);
        }
        return matMap;
    }

    private Node buildObject(JsonObject obj, Map<String, Shape3D> geoMap, Map<String, PhongMaterial> matMap) {
        String type = obj.get("type").getAsString();
        if ("Group".equals(type)) {
            Group group = new Group();
            JsonArray children = obj.getAsJsonArray("children");
            for (JsonElement childElem : children) {
                Node child = buildObject(childElem.getAsJsonObject(), geoMap, matMap);
                if (child != null) {
                    group.getChildren().add(child);
                }
            }
            applyMatrix(group, obj.getAsJsonArray("matrix"));
            return group;
        } else if ("Mesh".equals(type)) {
            String geoUuid = obj.get("geometry").getAsString();
            String matUuid = obj.get("material").getAsString();
            Shape3D shape = geoMap.get(geoUuid);
            if (shape != null) {
                shape.setMaterial(matMap.get(matUuid));
                applyMatrix(shape, obj.getAsJsonArray("matrix"));
                return shape;
            }
        }
        return null;
    }

    private void applyMatrix(Node node, JsonArray matrixArray) {
        if (matrixArray == null || matrixArray.size() != 16) return;
        double[] m = new double[16];
        for (int i = 0; i < 16; i++) {
            m[i] = matrixArray.get(i).getAsDouble();
        }
        Affine affine = new Affine(
                m[0], m[1], m[2], m[3],
                m[4], m[5], m[6], m[7],
                m[8], m[9], m[10], m[11]
        );
        node.getTransforms().add(affine);
    }

    private Color intToColor(int colorInt) {
        double r = ((colorInt >> 16) & 0xFF) / 255.0;
        double g = ((colorInt >> 8) & 0xFF) / 255.0;
        double b = (colorInt & 0xFF) / 255.0;
        return new Color(r, g, b, 1.0);
    }

    private MeshView createCone(double radius, double height, int segments) {
        TriangleMesh mesh = new TriangleMesh();
        int pointCount = segments + 2; // Apex + base points + base center
        float[] points = new float[pointCount * 3];
        float[] texCoords = new float[pointCount * 2];
        int faceCount = segments * 2; // Sides + base triangles
        int[] faces = new int[faceCount * 6];

        // Apex
        points[0] = 0;
        points[1] = (float) height / 2;
        points[2] = 0;
        texCoords[0] = 0.5f;
        texCoords[1] = 0f;

        // Base center
        int baseCenterIndex = segments + 1;
        points[baseCenterIndex * 3] = 0;
        points[baseCenterIndex * 3 + 1] = -(float) height / 2;
        points[baseCenterIndex * 3 + 2] = 0;
        texCoords[baseCenterIndex * 2] = 0.5f;
        texCoords[baseCenterIndex * 2 + 1] = 1f;

        // Base circle points
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            float x = (float) (radius * Math.cos(angle));
            float z = (float) (radius * Math.sin(angle));
            points[(i + 1) * 3] = x;
            points[(i + 1) * 3 + 1] = -(float) height / 2;
            points[(i + 1) * 3 + 2] = z;
            texCoords[(i + 1) * 2] = (float) i / segments;
            texCoords[(i + 1) * 2 + 1] = 1f;
        }

        // Side faces
        int idx = 0;
        for (int i = 0; i < segments; i++) {
            int p0 = 0; // Apex
            int p1 = i + 1;
            int p2 = (i + 1) % segments + 1;
            int t0 = 0;
            int t1 = i + 1;
            int t2 = (i + 1) % segments + 1;

            faces[idx++] = p0; faces[idx++] = t0;
            faces[idx++] = p2; faces[idx++] = t2;
            faces[idx++] = p1; faces[idx++] = t1;
        }

        // Base faces (note winding for outward normal)
        for (int i = 0; i < segments; i++) {
            int p0 = baseCenterIndex;
            int p1 = i + 1;
            int p2 = (i + 1) % segments + 1;
            int t0 = baseCenterIndex;
            int t1 = i + 1;
            int t2 = (i + 1) % segments + 1;

            faces[idx++] = p0; faces[idx++] = t0;
            faces[idx++] = p1; faces[idx++] = t1;
            faces[idx++] = p2; faces[idx++] = t2;
        }

        mesh.getPoints().addAll(points);
        mesh.getTexCoords().addAll(texCoords);
        mesh.getFaces().addAll(faces);

        return new MeshView(mesh);
    }

    private MeshView createPlane(double width, double height) {
        TriangleMesh mesh = new TriangleMesh();
        float hw = (float) width / 2;
        float hh = (float) height / 2;

        float[] points = {
                -hw, -hh, 0,
                hw, -hh, 0,
                hw, hh, 0,
                -hw, hh, 0
        };

        float[] texCoords = {
                0, 0,
                1, 0,
                1, 1,
                0, 1
        };

        int[] faces = {
                0, 0, 1, 1, 2, 2,
                0, 0, 2, 2, 3, 3
        };

        mesh.getPoints().addAll(points);
        mesh.getTexCoords().addAll(texCoords);
        mesh.getFaces().addAll(faces);

        return new MeshView(mesh);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
