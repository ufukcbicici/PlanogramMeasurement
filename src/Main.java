import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Core;

public class Main
{
    static {System.loadLibrary(Core.NATIVE_LIBRARY_NAME);}

    public static void main(String[] args)
    {
//        Mat mat = Mat.eye(3, 3, CvType.CV_8UC1);
//        System.out.println(mat.dump());
//        int x = 42;

        PlanogramMeasurement planogramMeasurement = new PlanogramMeasurement("src//planogram.json");
        var detections_json = PlanogramMeasurement.readJsonFromFile("src//detections_IMG_43699.json");
        // planogramToProductCompliance, productToPlanogramCompliance
        double [] results = planogramMeasurement.measurePlanogramCompliance(detections_json);
        double planogramToProductCompliance = results[0];
        double productToPlanogramCompliance = results[1];
        System.out.println("planogramToProductCompliance:"+planogramToProductCompliance);
        System.out.println("productToPlanogramCompliance:"+productToPlanogramCompliance);
    }
}