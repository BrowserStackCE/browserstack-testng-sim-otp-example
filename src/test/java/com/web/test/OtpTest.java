/* This script automates the 2FA login into amazon buisiness website using twilio messaging api*/
package com.web.test;

import io.restassured.path.json.JsonPath;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.openqa.selenium.Keys.TAB;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

import com.twilio.Twilio;
import com.twilio.base.ResourceSet;
import com.twilio.rest.api.v2010.account.Message;


public class OtpTest {

    private static final ThreadLocal<WebDriver> driverThread = new ThreadLocal<>();

    private static final String USERNAME = System.getenv("BROWSERSTACK_USERNAME");
    private static final String ACCESS_KEY = System.getenv("BROWSERSTACK_ACCESS_KEY");
    private static final String URL = "http://hub-cloud.browserstack.com/wd/hub";

    @BeforeTest(alwaysRun = true)
    @Parameters({"config", "environment"})
    public void setup(String configFile, String environment) throws MalformedURLException {
        JsonPath jsonPath = JsonPath.from(new File("src/test/resources/web/config/" + configFile));
        Map<String, String> capDetails = new HashMap<>();
        capDetails.putAll(jsonPath.getMap("capabilities"));
        capDetails.putAll(jsonPath.getMap("environments." + environment));
        DesiredCapabilities caps = new DesiredCapabilities(capDetails);
        caps.setCapability("browserstack.user", USERNAME);
        caps.setCapability("browserstack.key", ACCESS_KEY);
        driverThread.set(new RemoteWebDriver(new URL(URL), caps));
    }

    @Test
    @Parameters({"phone", "tsid", "ttkn"})
    public void testBStackDemoLogin ( String phone, String tsid, String ttkn ) throws InterruptedException {
        
        WebDriver driver = driverThread.get();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("https://www.amazon.in/ap/signin?openid.pape.max_auth_age=0&openid.return_to=https%3A%2F%2Fwww.amazon.in%2Fbusiness%2Fregister%2Fcheck%2Fstatus%3Fref_%3Dab_welcome_bw_ckab_dsk&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.assoc_handle=amzn_ab_reg_web_in&openid.mode=checkid_setup&marketPlaceId=A21TJRUUN4KGV&language=en_IN&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&pageId=ab_welcome_login_in&openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&ref_=ab_welcome_bw_ap-sn_dsk&disableLoginPrepopulate=1&switch_account=signin");

        //enter phone number & password
        wait.until(elementToBeClickable(By.xpath("//*[@id='ap_email']"))).sendKeys(phone);
        wait.until(elementToBeClickable(By.xpath("//*[@id='ap_password']"))).sendKeys("<YOUR AMAZON ACC PASSWORD>");
        //click on sign in
        wait.until(elementToBeClickable(By.xpath("//*[@id='signInSubmit']"))).click();
        
        //wait for the sms to go across
        TimeUnit.SECONDS.sleep(5);

        //read the otp from twilio
        Twilio.init(tsid, ttkn);
		String smsBody = getMessage(phone, tsid);
		String OTPNumber = smsBody.replaceAll("[^-?0-9]+", " ");
        
        //enter otp
        wait.until(elementToBeClickable(By.xpath("//*[@id='auth-mfa-otpcode']"))).sendKeys(OTPNumber);

        //click signin
        wait.until(elementToBeClickable(By.xpath("//*[@id='auth-signin-button']"))).click();
       
    }
    //get the last message for the specific phone number
    public static String getMessage(String phone, String tsid) {
		return getMessages(tsid).filter(m -> m.getDirection().compareTo(Message.Direction.INBOUND) == 0)
				.filter(m -> m.getTo().equals(phone)).map(Message::getBody).findFirst()
				.orElseThrow(IllegalStateException::new);
	}
    //get all messages
	private static Stream<Message> getMessages(String tsid) {
		ResourceSet<Message> messages = Message.reader(tsid).read();
		return StreamSupport.stream(messages.spliterator(), false);
	}

    @AfterTest(alwaysRun = true)
    public void teardown() {
        JavascriptExecutor js = (JavascriptExecutor) driverThread.get();
        js.executeScript("browserstack_executor: {\"action\": \"setSessionStatus\", \"arguments\": {\"status\": \"passed\", \"reason\": \"login passed\"}}");
        driverThread.get().quit();
        driverThread.remove();
    }

}
