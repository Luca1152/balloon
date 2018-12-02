package ro.luca1152.balloon.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import ro.luca1152.balloon.MyGame;
import ro.luca1152.balloon.utils.MapBodyBuilder;

public class Level {
    // Booleans
    public boolean isFinished = false;
    public boolean restart = false;

    // TiledMap
    private TiledMap tiledMap;
    private MapProperties mapProperties;
    private int mapWidth, mapHeight;

    // Box2D
    private World world;

    // Scene2D
    private Stage gameStage;

    // Render
    private OrthogonalTiledMapRenderer mapRenderer;

    // Entities
    private Array<Balloon> balloons;
    private Array<AirBlower> airBlowers;
    private Array<Hinge> hinges;
    private Array<RotatingPlatform> rotatingPlatforms;
    private Finish finish;

    Body body;

    public Level(int levelNumber) {
        // TiledMap
        tiledMap = MyGame.manager.get("maps/map-" + levelNumber + ".tmx", TiledMap.class);
        mapProperties = tiledMap.getProperties();
        mapWidth = (Integer) mapProperties.get("width");
        mapHeight = (Integer) mapProperties.get("height");

        // Box2D
        world = new World(new Vector2(0, -10f), true);
        Array<Body> solids = MapBodyBuilder.buildSolids(tiledMap, MyGame.PPM, world);

        // Scene2D
        gameStage = new Stage(new FitViewport(12f, 12f), MyGame.batch);

        // Balloons
        balloons = new Array<>();
        MapObjects balloonsObjects = tiledMap.getLayers().get("Balloons").getObjects();
        for (int object = 0; object < balloonsObjects.getCount(); object++) {
            Balloon balloon = new Balloon(world, ((RectangleMapObject) balloonsObjects.get(object)).getRectangle());
            balloons.add(balloon);
            gameStage.addActor(balloon);
        }

        // Air blowers
        airBlowers = new Array<>();
        if (tiledMap.getLayers().get("Air Blower") != null) {
            MapObjects airBlowersObjects = tiledMap.getLayers().get("Air Blower").getObjects();
            for (int object = 0; object < airBlowersObjects.getCount(); object++) {
                AirBlower airBlower = new AirBlower(world, (RectangleMapObject) airBlowersObjects.get(object));
                airBlowers.add(airBlower);
                gameStage.addActor(airBlower);
            }
        }

        // Hinges
        hinges = new Array<>();
        if (tiledMap.getLayers().get("Hinges") != null) {
            MapObjects hingesObjects = tiledMap.getLayers().get("Hinges").getObjects();
            for (int object = 0; object < hingesObjects.getCount(); object++) {
                Hinge hinge = new Hinge(world, (RectangleMapObject) hingesObjects.get(object));
                hinges.add(hinge);
            }
        }

        // Rotating platforms
        rotatingPlatforms = new Array<>();
        if (tiledMap.getLayers().get("Rotating Platforms") != null && hinges.size != 0) {
            MapObjects rotatingPlatformsObjects = tiledMap.getLayers().get("Rotating Platforms").getObjects();
            for (int object = 0; object < rotatingPlatformsObjects.getCount(); object++) {
                // Find the rotating platform's hinge
                Rectangle information = MapBodyBuilder.getInformation((RectangleMapObject) rotatingPlatformsObjects.get(object));
                Array<Hinge> hingesFound = new Array<>();
                world.QueryAABB(fixture -> {
                    if (fixture.getBody().getUserData() != null && fixture.getBody().getUserData().getClass() == Hinge.class) {
                        hingesFound.add((Hinge) fixture.getBody().getUserData());
                        return false; // Stop the search, the hinge was found
                    } else
                        return true; // Continue the search
                }, information.x, information.y, information.x + information.width, information.y + information.height);
                RotatingPlatform rotatingPlatform = new RotatingPlatform(world, (RectangleMapObject) rotatingPlatformsObjects.get(object), hingesFound.get(0));
                rotatingPlatforms.add(rotatingPlatform);
            }
        }

        // Finish
        finish = new Finish(((RectangleMapObject) tiledMap.getLayers().get("Finish").getObjects().get(0)).getRectangle());
        gameStage.addActor(finish);

        // Hinges

        // Render
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, 1 / MyGame.PPM, MyGame.batch);

        // InputProcessor
        Gdx.input.setInputProcessor(new InputMultiplexer(
                gameStage,
                new InputAdapter() {
                    @Override
                    public boolean keyDown(int keycode) {
                        if (keycode == Input.Keys.SPACE)
                            restart = true;
                        return true;
                    }
                }));
    }

    public void draw() {
        MyGame.batch.setProjectionMatrix(gameStage.getCamera().combined);
        gameStage.draw();
        mapRenderer.render();
        MyGame.debugRenderer.render(world, gameStage.getCamera().combined);
    }

    public void update(float delta) {
        gameStage.act(delta);
        listenForCollisions();
        makeCameraFollowPlayer();
        world.step(1 / 60f, 6, 2);
    }

    private void makeCameraFollowPlayer() {
        Vector2 balloonPos = balloons.get(0).body.getWorldCenter();
        gameStage.getCamera().position.lerp(new Vector3(balloonPos.x, balloonPos.y, 0f), .15f);
        keepCameraWithinBounds();
        gameStage.getCamera().update();
    }

    private void keepCameraWithinBounds() {
        OrthographicCamera camera = (OrthographicCamera) gameStage.getCamera();

        float mapLeft = 0f, mapRight = mapWidth;
        if (mapWidth > camera.viewportWidth) {
            mapLeft = -1;
            mapRight = mapWidth + 1;
        }
        float mapBottom = 0f, mapTop = mapHeight;
        float cameraHalfWidth = camera.viewportWidth / 2f, cameraHalfHeight = camera.viewportHeight / 2f;
        float cameraLeft = camera.position.x - cameraHalfWidth, cameraRight = camera.position.x - cameraHalfWidth;
        float cameraBottom = camera.position.y - cameraHalfHeight, cameraTop = camera.position.y + cameraHalfHeight;

        // Clam horizontal axis
        if (camera.viewportWidth > mapRight) camera.position.x = mapRight / 2f;
        else if (cameraLeft <= mapLeft) camera.position.x = mapLeft + cameraHalfWidth;
        else if (cameraRight >= mapRight) camera.position.x = mapRight - cameraHalfWidth;

        // Clamp vertical axis
        if (camera.viewportHeight > mapTop) camera.position.y = mapTop / 2f;
        else if (cameraBottom <= mapBottom) camera.position.y = mapBottom + cameraHalfHeight;
        else if (cameraTop >= mapTop) camera.position.y = mapTop - cameraHalfHeight;
    }

    private void listenForCollisions() {
        if (!restart)
            for (Balloon balloon : balloons) {
                if (balloon.getCollisionBox().overlaps(finish.getCollisionBox())) {
                    isFinished = true;
                }
            }
    }
}
