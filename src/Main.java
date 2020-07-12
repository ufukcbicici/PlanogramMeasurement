import org.opencv.core.Core;

import java.io.FileWriter;
import java.io.IOException;

public class Main
{
    static {System.loadLibrary(Core.NATIVE_LIBRARY_NAME);}

    public static void main(String[] args)
    {
        var planogram_json = args[0];
        var detection_json = args[1];
        var detection_img_file = args[2];
        var compliancePath = args[3];
        var resultImagePath = args[4];
        System.out.println(planogram_json);
        System.out.println(detection_json);
        PlanogramMeasurement planogramMeasurement = new PlanogramMeasurement(planogram_json,
                compliancePath, resultImagePath);
        var detections_json = PlanogramMeasurement.readJsonFromFile(detection_json);
        double [] compliances = planogramMeasurement.measurePlanogramCompliance(detections_json, detection_img_file);
        // planogramToProductCompliances, productToPlanogramCompliances
//        double [] detectionsArr = {planogramToProductCompliances[0], planogramToProductCompliances[1],
//                productToPlanogramCompliances[0], productToPlanogramCompliances[1]};

        try {
            FileWriter writer = new FileWriter(compliancePath + "\\compliance.txt", false);
            writer.write("planogramToProductCompliance:"+compliances[0]+"\n");
            writer.write("productToPlanogramCompliance:"+compliances[1]+"\n");
            writer.write("meanCompliance:"+(compliances[0]+compliances[1])*0.5+"\n");
            writer.close();
        } catch (IOException e) {
            System.out.println("IO Error");
        }
        System.out.println("x");
    }
}