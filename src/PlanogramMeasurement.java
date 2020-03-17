import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class Product
{
    private long label;
    private long left;
    private long top;
    private long right;
    private long bottom;
    private Mat image;

    public Product(long _label, long l, long t, long r, long b)
    {
        label = _label;
        left = l;
        top = t;
        right = r;
        bottom = b;
        image = null;
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

    public Mat getImage(){return image;}

    public void setImage(Mat _image){image = _image;}
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
    private ArrayList<Product> planogramProducts;

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

    public double [] measurePlanogramCompliance(JSONObject detectionsJson)
    {
        // Step 1: Interpret the detection json
        List<Product> detectedProductsList = interpretDetectionsJson(detectionsJson);
        // Step 2: Create a canvas image for every detection
        createProductImages((int)detectionImageWidth, (int)detectionImageHeight, detectedProductsList);
        // Step 3: Calculate the homography transformation
        calculateHomographyMapping();
        // Step 4: Measure planogram compliance
        // Planogram vs Products
        double planogramToProductCompliance = calculateCompliance(planogramProducts, detectedProductsList);
        // Products vs Planogram
        double productToPlanogramCompliance = calculateCompliance(detectedProductsList, planogramProducts);
        double [] resultArray = {planogramToProductCompliance, productToPlanogramCompliance};
        clearMemory();
        return resultArray;
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
        planogramProducts = new ArrayList<>();
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
                planogramProducts.add(product);
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

    private void createProductImages(int width, int height, List<Product> productList)
    {
        // Draw a canvas for every detection:
        // The reason is to eliminate the amount of occlusions between the detections.
        imageScaleRatio = maxDetectionWidth / (double)width;
        detectionImageScaledWidth = (int)maxDetectionWidth;
        detectionImageScaledHeight = (int)(imageScaleRatio * height);
        // Core.multiply(canvas, new Scalar(255, 255, 255), canvas);
        int productId = 0;
        for(var product: productList) {
            Mat canvas = Mat.zeros(detectionImageScaledHeight, detectionImageScaledWidth, CvType.CV_8U);
            // Draw detection on the canvas
            var topLeftPoint = new Point((int) (imageScaleRatio * (double) product.getLeft()),
                    (int) (imageScaleRatio * (double) product.getTop()));
            var bottomRightPoint = new Point((int) (imageScaleRatio * (double) product.getRight()),
                    (int) (imageScaleRatio * (double) product.getBottom()));
            Imgproc.rectangle(canvas, topLeftPoint, bottomRightPoint, new Scalar(255), -1);
            product.setImage(canvas);
            //Imgcodecs.imwrite("detection_product"+productId+".png", canvas);
            productId++;
        }
    }

    private void calculateHomographyMapping()
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

        //Calculate the homography
        Mat H = Calib3d.findHomography( sourceMat, destinationMat, 0);
        int productId = 0;
        for(var shelf: shelves)
        {
            for (var product : shelf.getProducts())
            {
                // Draw a single product on the canvas
                Mat planogramImage = Mat.zeros((int)dolapHeight, (int)dolapWidth, CvType.CV_8U);
                var topLeftPoint = new Point((int)product.getLeft(), (int)product.getTop());
                var bottomRightPoint = new Point((int)product.getRight(), (int) product.getBottom());
                Mat warpedPlanogram = new Mat();
                Imgproc.rectangle(planogramImage, topLeftPoint, bottomRightPoint, new Scalar(255), -1);
                Imgproc.warpPerspective(planogramImage, warpedPlanogram, H,
                        new Size(detectionImageScaledWidth, detectionImageScaledHeight));
                product.setImage(warpedPlanogram);
                //Imgcodecs.imwrite("warped_planogram_image_product_"+productId+".png", warpedPlanogram);
                productId++;
            }
        }
    }

    // Compare list1 vs list2
    private double calculateCompliance(List<Product> list1, List<Product> list2)
    {
        int productId = 0;
        double totalIoU = 0.0;
        for(var product1: list1)
        {
            //System.out.println("Product:"+productId);
            double maxIoUWithCorrectProduct = 0.0;
            for(var product2: list2)
            {
                // Convert two images to binary
                Mat binaryObject1 = PlanogramMeasurement.binarizeImage(product1.getImage());
                Mat binaryObject2 = PlanogramMeasurement.binarizeImage(product2.getImage());
                //Imgcodecs.imwrite("planogram_binary.png", binaryObject1);
                //Imgcodecs.imwrite("object_binary.png", binaryObject2);
                // Take the union by bitwise OR.
                Mat unionImage = new Mat();
                Core.bitwise_or(binaryObject1, binaryObject2, unionImage);
                //Imgcodecs.imwrite("unionImage.png", unionImage);
                // Take the intersection by bitwise AND.
                Mat intersectionImage = new Mat();
                Core.bitwise_and(binaryObject1, binaryObject2, intersectionImage);
                //Imgcodecs.imwrite("intersectionImage.png", intersectionImage);
                // Calculate pixelwise IoU metric
                double IoU = 0.0;
                if(product1.getLabel() == product2.getLabel()) {
                    var numOfIntersectionPixels = Core.sumElems(intersectionImage).val[0] / 255.0;
                    var numOfUnionPixels = Core.sumElems(unionImage).val[0] / 255.0;
                    IoU = numOfIntersectionPixels / numOfUnionPixels;
                    if (IoU > maxIoUWithCorrectProduct)
                    {
                        maxIoUWithCorrectProduct = IoU;
                    }
                }
                //System.out.println("IoU="+IoU);
            }
            productId++;
            totalIoU += maxIoUWithCorrectProduct;
        }
        double meanIoU = totalIoU / list1.size();
        return meanIoU;
    }

    private void clearMemory()
    {
        for(var shelf: shelves) {
            for (var product : shelf.getProducts()) {
                product.setImage(null);
            }
        }
    }

    // Utility Functions for low level image processing
    private static Mat binarizeImage(Mat image)
    {
//        Mat grayscale = new Mat();
//        Imgproc.cvtColor(colorImage, grayscale, Imgproc.COLOR_BGR2GRAY);
        Mat binary = new Mat();
        Imgproc.threshold(image, binary, 0 , 255, Imgproc.THRESH_BINARY);
        return binary;
    }
}
