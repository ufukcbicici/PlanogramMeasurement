import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.nio.ByteBuffer;

class Product
{
    private long label;
    private long left;
    private long top;
    private long right;
    private long bottom;

    public Product(long _label, long l, long t, long r, long b)
    {
        label = _label;
        left = l;
        top = t;
        right = r;
        bottom = b;
    }

    public long getLabel() {
        return label;
    }

    public long getLeft() {
        return left;
    }

    public long getTop() {
        return top;
    }

    public long getRight() {
        return right;
    }

    public long getBottom() {
        return bottom;
    }
}


class Shelf
{
    private long left;
    private long top;
    private long right;
    private long bottom;

    public ArrayList<Product> getProducts() {
        return products;
    }

    private ArrayList<Product> products ;

    public Shelf(long l, long t, long r, long b)
    {
        left = l;
        top = t;
        right = r;
        bottom = b;
        products = new ArrayList<>();
    }

    public void addProduct(Product p)
    {
        products.add(p);
    }

    public long getLeft() {
        return left;
    }

    public long getTop() {
        return top;
    }

    public long getRight() {
        return right;
    }

    public long getBottom() {
        return bottom;
    }
}


public class PlanogramMeasurement {

    private JSONObject planogramJson;
    private long dolapTop;
    private long dolapLeft;
    private long dolapBottom;
    private long dolapRight;
    private long dolapWidth;
    private long dolapHeight;
    private ArrayList<Shelf> shelves;

    private long detectionImageWidth;
    private long detectionImageHeight;
    private int detectionImageScaledWidth;
    private int detectionImageScaledHeight;

    private long detectionCornersX1;
    private long detectionCornersY1;
    private long detectionCornersX2;
    private long detectionCornersY2;
    private long detectionCornersX3;
    private long detectionCornersY3;
    private long detectionCornersX4;
    private long detectionCornersY4;

    private long maxDetectionWidth = 640;
    private double imageScaleRatio;

    public PlanogramMeasurement()
    {

    }

    public PlanogramMeasurement(String planogram_file_path)
    {
        planogramJson = PlanogramMeasurement.readJsonFromFile(planogram_file_path);
        interpretPlanogramJson();
    }

    public void measurePlanogramCompliance(JSONObject detectionsJson)
    {
        // Step 1: Interpret the detection json
        List<Product> detectedProductsList = interpretDetectionsJson(detectionsJson);
        // Step 2: Create the color codes for all products in the detection json and planogram json
        Dictionary<Long, IntBuffer> colorDictionary = createColorDictionary(detectedProductsList);
        // Step 3: Create a canvas image for every detection
        List<Mat> detectionCanvasList = createProductImages((int)detectionImageWidth, (int)detectionImageHeight,
                detectedProductsList, colorDictionary);
        // Step 4: Calculate the homography transformation
        Mat warpedPlaongram = mapPlanogramToDetection(colorDictionary);
        // Step 5: Calculate the pixelwise correspondence of the warped planogram with the detections.

        System.out.println("X");
    }

    public static JSONObject readJsonFromFile(String json_path)
    {
        JSONObject jsonObject = null;
        // Read Planogram Json as Text File
        try {
            JSONParser jsonParser = new JSONParser();
            FileReader fileReader = new FileReader(json_path);
            jsonObject = (JSONObject)jsonParser.parse(fileReader);
        } catch (FileNotFoundException e) {
            System.err.println("Planogram Json cannot be read.");
            e.printStackTrace();
        } catch (ParseException e) {
            System.err.println("Planogram Json cannot be parsed.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Planogram Json IO error.");
            e.printStackTrace();
        }
        return jsonObject;
    }

    private void interpretPlanogramJson()
    {
        JSONObject dolapObject = (JSONObject)planogramJson.get("DOLAP");
        // Get Complete Shelf dimensions
        dolapLeft = (long)dolapObject.get("X");
        dolapTop = (long)dolapObject.get("Y");
        dolapWidth = (long)dolapObject.get("W");
        dolapHeight =  (long)dolapObject.get("H");
        dolapRight = dolapLeft + dolapWidth;
        dolapBottom = dolapTop + dolapHeight;
        // Get all shelves
        shelves = new ArrayList<>();
        JSONArray rafArray = (JSONArray)dolapObject.get("RAFLAR");
        for(Object obj: rafArray) {
            JSONObject rafObj = (JSONObject) obj;
            var shelf_left = (long) rafObj.get("X");
            var shelf_top = (long) rafObj.get("Y");
            var shelf_right = shelf_left + (long) rafObj.get("W");
            var shelf_bottom = shelf_top + (long) rafObj.get("H");

            var shelf = new Shelf(shelf_left, shelf_top, shelf_right, shelf_bottom);
            JSONArray productsInShelf = (JSONArray) rafObj.get("URUNLER");
            for (Object pObj : productsInShelf) {
                JSONObject productObj = (JSONObject) pObj;
                var product_left = (long) productObj.get("X");
                var product_top = (long) productObj.get("Y");
                var product_right = product_left + (long) productObj.get("W");
                var product_bottom = product_top + (long) productObj.get("H");
                var product_label = (long) productObj.get("SINIF");
                var product = new Product(product_label, product_left, product_top, product_right, product_bottom);
                shelf.addProduct(product);
            }
            shelves.add(shelf);
        }
    }

    private List<Product> interpretDetectionsJson(JSONObject detectionsJsonObject)
    {
        List<Product> detectedProductsList = new ArrayList<>();
        detectionImageWidth = (long)detectionsJsonObject.get("image_width");
        detectionImageHeight = (long)detectionsJsonObject.get("image_height");
        detectionCornersX1 = (long)detectionsJsonObject.get("corner_x1");
        detectionCornersY1 = (long)detectionsJsonObject.get("corner_y1");
        detectionCornersX2 = (long)detectionsJsonObject.get("corner_x2");
        detectionCornersY2 = (long)detectionsJsonObject.get("corner_y2");
        detectionCornersX3 = (long)detectionsJsonObject.get("corner_x3");
        detectionCornersY3 = (long)detectionsJsonObject.get("corner_y3");
        detectionCornersX4 = (long)detectionsJsonObject.get("corner_x4");
        detectionCornersY4 = (long)detectionsJsonObject.get("corner_y4");
        JSONArray productsInShelf = (JSONArray)detectionsJsonObject.get("detections");
        for(Object pObj: productsInShelf)
        {
            //{"class": 37, "left": 825.5088559985161, "top": 1742.5092352628708, "right": 966.3706343173981, "bottom": 2194.6446303129196}
            JSONObject productObj = (JSONObject) pObj;
            var product_left = (double) productObj.get("left");
            var product_top = (double) productObj.get("top");
            var product_right = (double) productObj.get("right");
            var product_bottom = (double) productObj.get("bottom");
            var product_label = (long) productObj.get("class");
            var product = new Product(product_label, (long)product_left, (long)product_top,
                    (long)product_right, (long)product_bottom);
            detectedProductsList.add(product);
        }
        return detectedProductsList;
    }

    private Dictionary<Long, IntBuffer> createColorDictionary(List<Product> detectedProductsList)
    {
        HashSet<Long> idSet = new HashSet<>();
        // Add planogram ids
        for(var shelf: shelves)
        {
            for(var product: shelf.getProducts())
            {
                idSet.add(product.getLabel());
            }
        }
        // Add detection ids
        for(var product: detectedProductsList)
        {
            idSet.add(product.getLabel());
        }
        HashSet<IntBuffer> colorSet = new HashSet<>();
        Dictionary<Long, IntBuffer> colorDictionary = new Hashtable<>();
        for(Long id: idSet)
        {
            while (true)
            {
                int [] colorArray = {(int)(255.0 * Math.random()), (int)(255.0 * Math.random()),
                        (int)(255.0 * Math.random())};
                var colorBuffer = IntBuffer.wrap(colorArray);
                if (!colorSet.contains(colorBuffer))
                {
                    colorSet.add(colorBuffer);
                    colorDictionary.put(id, colorBuffer);
                    break;
                }
            }
        }
        return colorDictionary;
    }

    private List<Mat> createProductImages(int width, int height,
                                    List<Product> productList, Dictionary<Long, IntBuffer> colorDictionary)
    {
        // Draw a canvas for every detection:
        // The reason is to eliminate the amount of occlusions between the detections.
        imageScaleRatio = maxDetectionWidth / (double)width;
        detectionImageScaledWidth = (int)maxDetectionWidth;
        detectionImageScaledHeight = (int)(imageScaleRatio * height);
        List<Mat> detectionCanvasList = new ArrayList<>();
        // Core.multiply(canvas, new Scalar(255, 255, 255), canvas);
        int productId = 0;
        for(var product: productList) {
            Mat canvas = Mat.zeros(detectionImageScaledHeight, detectionImageScaledWidth, CvType.CV_8UC3);
            // Draw detection on the canvas
            var productColorArray = colorDictionary.get(product.getLabel()).array();
            var productColor = new Scalar(productColorArray[0], productColorArray[1], productColorArray[2]);
            var topLeftPoint = new Point((int) (imageScaleRatio * (double) product.getLeft()),
                    (int) (imageScaleRatio * (double) product.getTop()));
            var bottomRightPoint = new Point((int) (imageScaleRatio * (double) product.getRight()),
                    (int) (imageScaleRatio * (double) product.getBottom()));
            Imgproc.rectangle(canvas, topLeftPoint, bottomRightPoint, productColor, -1);
            detectionCanvasList.add(canvas);
            Imgcodecs.imwrite("detection_product"+productId+".png", canvas);
            productId++;
        }
        return detectionCanvasList;
    }

    private Mat mapPlanogramToDetection(Dictionary<Long, IntBuffer> colorDictionary)
    {
        //This is the destination
        List<Point>  detectionPoints = new ArrayList<>();
        MatOfPoint2f destinationMat = new MatOfPoint2f();
        detectionPoints.add(new Point(imageScaleRatio * (double)detectionCornersX1, imageScaleRatio * (double)detectionCornersY1));
        detectionPoints.add(new Point(imageScaleRatio * (double)detectionCornersX2, imageScaleRatio * (double)detectionCornersY2));
        detectionPoints.add(new Point(imageScaleRatio * (double)detectionCornersX3, imageScaleRatio * (double)detectionCornersY3));
        detectionPoints.add(new Point(imageScaleRatio * (double)detectionCornersX4, imageScaleRatio * (double)detectionCornersY4));
        destinationMat.fromList(detectionPoints);

        //This is the source
        List<Point> planogramPoints = new ArrayList<>();
        MatOfPoint2f sourceMat = new MatOfPoint2f();
        planogramPoints.add(new Point((double)dolapLeft, (double)dolapTop));
        planogramPoints.add(new Point((double)(dolapLeft + dolapWidth), (double)dolapTop));
        planogramPoints.add(new Point((double)(dolapLeft + dolapWidth), (double)(dolapTop + dolapHeight)));
        planogramPoints.add(new Point((double)dolapLeft, (double)(dolapTop + dolapHeight)));
        sourceMat.fromList(planogramPoints);

        Mat planogramImage = Mat.zeros((int)dolapHeight, (int)dolapWidth, CvType.CV_8UC3);
        for(var shelf: shelves)
        {
            for(var product: shelf.getProducts())
            {
                // Draw products on the canvas
                var productColorArray = colorDictionary.get(product.getLabel()).array();
                var productColor = new Scalar(productColorArray[0], productColorArray[1], productColorArray[2]);
                var topLeftPoint = new Point((int)product.getLeft(), (int)product.getTop());
                var bottomRightPoint = new Point((int)product.getRight(), (int) product.getBottom());
                Imgproc.rectangle(planogramImage, topLeftPoint, bottomRightPoint, productColor, -1);
            }
        }
        //Visualize
        Imgcodecs.imwrite("planogram_image.png", planogramImage);
        //Calculate the homography
        Mat H = Calib3d.findHomography( sourceMat, destinationMat, 0);
        /*
        System.out.println(sourceMat.dump());
        System.out.println(destinationMat.dump());
        System.out.println(H.dump());
         */
        // Apply the Homography: Planogram mapped to the incoming image
        Mat warpedPlanogram = new Mat();
        Imgproc.warpPerspective(planogramImage, warpedPlanogram, H,
                new Size(detectionImageScaledWidth, detectionImageScaledHeight));
        Imgcodecs.imwrite("warped_planogram_image.png", warpedPlanogram);
        return warpedPlanogram;
    }
}
