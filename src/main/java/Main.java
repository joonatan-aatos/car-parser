import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {

    static class CarData {
        public String name;
        public int count;
        public int capacity;
        public int range;
        public ArrayList<String> supportedChargers;
        public int maxChargingPower;
    }

    public static void main(String[] args) throws Exception {
        final InputStream in = Main.class.getResourceAsStream("autot.csv");
        final BufferedReader br = new BufferedReader(new InputStreamReader(in));
        final int skipToLine = 12;
        final int lastLine = 6579;
        final int minCarCount = 10;
        
        // Skip lines
        for (int i = 0; i < skipToLine - 1; i++) {
            br.readLine();
        }

        ArrayList<CarData> cars = new ArrayList<>();

        // Get all relevant cars
        String line;
        int index = 0;
        while ((line = br.readLine()) != null && index <= lastLine) {
            if (line.length() <= 0) break;
            String[] data = line.split(";");
            if (!data[5].equals(".") && !data[1].contains("Yhteensä")) {
                int count = Integer.parseInt(data[5].replaceAll(",", ""));
                if (count >= minCarCount) {
                    String name =
                            data[0].replaceAll(String.valueOf((char) 160), "") + "_" +
                            data[1].replaceAll(String.valueOf((char) 160), "");
                    CarData car = new CarData();
                    car.name = name;
                    car.count = count;
                    cars.add(car);
                }
            }
            index++;
        }

        // Get car info from https://pod-point.com/guides/vehicles/ with celenium
        WebDriver driver = new FirefoxDriver();
        try {
            driver.get("https://pod-point.com/guides/vehicles/");
            List<WebElement> elements = driver.findElements(By.cssSelector("div.selectable-vehicle-card"));
            for (WebElement element : elements) {
                WebElement typeElement = element.findElement(By.cssSelector("div:nth-child(1) > a:nth-child(1) > div:nth-child(3) > ul:nth-child(2) > li:nth-child(1)"));
                System.out.println(typeElement.getText());
            }
        } finally {
            driver.quit();
        }

        FileWriter myWriter = new FileWriter("CarType.java");
        for (CarData car: cars) {
            myWriter.write(car.name + ";" + car.count + "\n");
        }
        myWriter.close();
        System.out.println("Total: " + cars.size());
    }
}