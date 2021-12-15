import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    static class CarData {
        public String name;
        public int count;
        public double capacity;
        public double drivingEfficiency;
        public ArrayList<String> supportedChargers;
        public double maxChargingPowerAC;
        public double maxChargingPowerDC;
        public String link;
    }

    public static double findDoubleAfter(String regex, String text) {
        Matcher matcher = Pattern.compile(regex).matcher(text);

        if (!matcher.find()) {
            return -1;
        }
        int startIndex = matcher.end();
        String[] values = text.substring(startIndex).split(" |\n");
        if (values[0].equals("N/A"))
            return -1;
        double value = Double.parseDouble(values[0]);
        if (values[1].equals("-")) {
            value = (Double.parseDouble(values[0]) + Double.parseDouble(values[2])) / 2;
        }
        return value;
    }

    public static String formatSupportedChargers(ArrayList<String> chargers) {
        if (chargers == null)
            return "";
        StringBuilder s = new StringBuilder();
        for (String charger : chargers) {
            s.append("\"").append(charger).append("\", ");
        }
        s.delete(s.length() - 2, s.length());
        return s.toString();
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
                    name = name.replaceAll("-", "_").replaceAll(" ", "_").replaceAll("!", "1").replaceAll("BMWI", "BMW").replaceAll("BENZ_", "").replaceAll("TESLAMOTORS", "TESLA").replaceAll("MODEL", "MODEL_").replaceAll("EQC400", "EQC").replaceAll("PEUGEOT_", "PEUGEOT_E_").replaceAll("VOLKSWAGEN_", "VOLKSWAGEN_E_");
                    if (name.equals("VOLKSWAGEN_E_ID.3")) name = "VOLKSWAGEN_ID_3";
                    if (name.equals("PEUGEOT_E_ION")) name = "PEUGEOT_ION";
                    if (name.equals("FIAT_500")) name = "FIAT_500E";
                    if (name.equals("MERCEDES_V_KLASSE")) name = "MERCEDES_EQV";
                    if (name.equals("MINI_COOPERSE")) name = "MINI_COOPER_SE";
                    boolean carExists = false;
                    for (CarData car : cars) {
                        if (Objects.equals(car.name, name)) {
                            car.count += count;
                            carExists = true;
                            break;
                        }
                    }
                    if (!carExists) {
                        CarData car = new CarData();
                        car.name = name;
                        car.count = count;
                        cars.add(car);
                    }
                }
            }
            index++;
        }

        // Get car info from https://pod-point.com/guides/vehicles/ with selenium
        WebDriver driver = new FirefoxDriver();
        try {
            // Find links for cars
            driver.get("https://pod-point.com/guides/vehicles/");
            List<WebElement> elements = driver.findElements(By.cssSelector("div.selectable-vehicle-card"));
            for (WebElement element : elements) {
                String[] elementData = element.getText().split("\n");
                String carName = elementData[0].replaceAll("-", "_").replaceAll(" ", "_").replaceAll("!", "1").replaceAll("_E_", "_").replaceAll("\\.", "_").toUpperCase();
                carName = Normalizer.normalize(carName, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "");
                if (carName.equals("MINI_ELECTRIC_(2020)")) carName = "MINI_COOPER_SE";
                for (CarData carData : cars) {
                    if (carData.link != null)
                        continue;
                    if (carName.contains(carData.name)) {
                        carData.link = element.findElement(By.cssSelector("a")).getAttribute("href");
                        break;
                    }
                }
            }

            // Go through all found links and get data
            for (CarData carData : cars) {
                if (carData.link == null) {
                    System.out.println("No link found for "+carData.name);
                    continue;
                }
                driver.get(carData.link);

                WebElement carNameContainer = driver.findElement(By.cssSelector(".p-v-xl > div:nth-child(1) > div:nth-child(1)"));
                String carName = carNameContainer.findElement(By.cssSelector("h1")).getText();
                System.out.println("Link found for "+carData.name+": "+carName);

                WebElement batteryInfoContainer = driver.findElement(By.cssSelector("#article-block-10 > div:nth-child(1) > div:nth-child(1) > div:nth-child(1)"));
                String batteryText = batteryInfoContainer.getText();
                carData.capacity = findDoubleAfter("Battery size ", batteryText);
                double battEff = findDoubleAfter("efficiency \\(NEDC\\) ", batteryText);
                if (battEff < 0) {
                    battEff = findDoubleAfter("efficiency \\(WLTP\\) ", batteryText);
                }
                if (battEff < 0) {
                    battEff = findDoubleAfter("efficiency \\(Pod Point estimate\\) ", batteryText);
                }
                if (battEff < 0) {
                    System.out.println("No driving efficiency found for " + carData.name);
                    carData.drivingEfficiency = -1;
                } else {
                    carData.drivingEfficiency = battEff / 16.09344; //  Wh/mile  to  kWh/100km  conversion
                }

                // Chargers
                carData.maxChargingPowerAC = 0;
                carData.maxChargingPowerDC = 0;
                carData.supportedChargers = new ArrayList<>();
                WebElement chargerInfoContainer = driver.findElement(By.cssSelector("#article-block-4 > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > table:nth-child(3) > tbody:nth-child(1) > tr:nth-child(1)"));
                String chargerText = chargerInfoContainer.getText();
                chargerText = chargerText.replaceAll("\n\n", "");
                System.out.println(chargerText);
                String[] chargerTextLines = chargerText.split("\n");
                for (String chargerLine : chargerTextLines) {
                    if (chargerLine.contains("Max AC 3-phase rate: ")) {
                        if (chargerLine.charAt(chargerLine.length()-1) == '*') {
                            chargerLine = chargerLine.substring(0, chargerLine.length()-1);
                        }
                        chargerLine = chargerLine.replaceAll("Max AC 3-phase rate: ", "").replaceAll("kW", "");
                        carData.maxChargingPowerAC = Double.parseDouble(chargerLine);
                    }
                    else if (chargerLine.contains("Max DC rate: ")) {
                        chargerLine = chargerLine.replaceAll("Max DC rate: ", "").replaceAll("Rapid: CHAdeMO", "").replaceAll("kW", "");
                        if (chargerLine.contains("-")) {
                            String[] values = chargerLine.split(" - ");
                            if (values.length == 1)
                                values = chargerLine.split("-");
                            carData.maxChargingPowerDC = (Double.parseDouble(values[0]) + Double.parseDouble(values[1])) / 2;
                        }
                        else {
                            carData.maxChargingPowerDC = Double.parseDouble(chargerLine);
                        }
                    }
                    if (chargerLine.contains("Slow / Fast: Type 2") || chargerLine.contains("Slow / Fast: Type 1")) {
                        carData.supportedChargers.add("Type2");
                    }
                    else if (chargerLine.contains("Rapid: CHAdeMO")) {
                        carData.supportedChargers.add("CHAdeMO");
                    }
                    else if (chargerLine.contains("Rapid: CCS Supercharger") || chargerLine.contains("Rapid: Supercharger")) {
                        carData.supportedChargers.add("Tesla");
                    }
                    else if (chargerLine.contains("Rapid: CCS")) {
                        carData.supportedChargers.add("CCS");
                    }
                }
                System.out.println();

            }
        } finally {
            driver.quit();
        }

        FileWriter myWriter = new FileWriter("CarType.java");
        for (CarData car: cars) {
            myWriter.write(
                    String.format(
                            "%s(%d, %.1f, %.1f, %.1f, %.1f, new ArrayList<String>(Arrays.asList(%s))), %s\n",
                            car.name,
                            car.count,
                            car.capacity,
                            car.drivingEfficiency,
                            car.maxChargingPowerAC,
                            car.maxChargingPowerDC,
                            formatSupportedChargers(car.supportedChargers),
                            (car.link == null)?"// No link found":""
                    )
            );
        }
        myWriter.close();
        System.out.println("Total: " + cars.size());
    }
}