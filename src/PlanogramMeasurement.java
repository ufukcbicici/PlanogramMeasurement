import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Core;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
}


class Shelf
{
    private long left;
    private long top;
    private long right;
    private long bottom;
    private ArrayList<Product> products;

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

    public PlanogramMeasurement()
    {

    }

    public PlanogramMeasurement(String planogram_file_path)
    {
        planogramJson = readJsonFromFile(planogram_file_path);
        interpretPlanogramJson();
    }

    public JSONObject readJsonFromFile(String json_path)
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
        for(Object obj: rafArray)
        {
            JSONObject rafObj = (JSONObject)obj;
            var shelf_left = (long)rafObj.get("X");
            var shelf_top = (long)rafObj.get("Y");
            var shelf_right = shelf_left + (long)rafObj.get("W");
            var shelf_bottom = shelf_top + (long)rafObj.get("H");

            var shelf = new Shelf(shelf_left, shelf_top, shelf_right, shelf_bottom);
            JSONArray productsInShelf = (JSONArray)rafObj.get("URUNLER");
            for(Object pObj: productsInShelf)
            {
                JSONObject productObj = (JSONObject)pObj;
                var product_left = (long)productObj.get("X");
                var product_top = (long)productObj.get("Y");
                var product_right = product_left + (long)productObj.get("W");
                var product_bottom = product_top + (long)productObj.get("H");
                var product_label = (long)productObj.get("SINIF");
                var product = new Product(product_label, product_left, product_top, product_right, product_bottom);
                shelf.addProduct(product);
            }
            shelves.add(shelf);
        }
    }

    private void createColorDictionary(HashSet<Long> idSet)
    {
        HashSet<ByteBuffer> colorSet = new HashSet<>();
        Dictionary<Long, ByteBuffer> colorDictionary = new Hashtable<>();
        for(Long id: idSet)
        {
            while (true)
            {
                byte [] colorArray = {(byte)(255.0 * Math.random()),
                                      (byte)(255.0 * Math.random()),
                                      (byte)(255.0 * Math.random())};
                var colorBuffer = ByteBuffer.wrap(colorArray);
                if (!colorSet.contains(colorBuffer))
                {
                    colorSet.add(colorBuffer);
                    colorDictionary.put(id, colorBuffer);
                    break;
                }
            }
        }

    }
}
