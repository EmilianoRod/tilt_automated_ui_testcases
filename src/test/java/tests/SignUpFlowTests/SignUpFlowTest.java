package tests.SignUpFlowTests;



import Utils.Config;
import pages.SignUp.TestUsers;
import pages.SignUp.TestUsers.UiUser;
import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.SignUp.SignUpPage;
import pages.menuPages.DashboardPage;

import java.time.Duration;

public class SignUpFlowTest extends BaseTest {


    @Test(groups = "ui-only",
            description = "New user can complete 2-step Sign Up and then log in")
    public void newUserCanSignUpAndLogin() throws Exception {

        UiUser user = TestUsers.newMailSlurpUserForSignup();

        // Step 1: navigate to Sign Up
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();

        // go to sign-up via UI link if needed
        // driver().findElement(By.linkText("Sign Up")).click();
        // or direct:
        SignUpPage signUp = new SignUpPage(driver());
        signUp.navigateTo();

        // Step 2: complete 2-step sign up
        signUp.completeSignUp(
                user.firstName,
                user.lastName,
                user.email,
                user.password
        );

        // After sign-up we should be on the Sign In page
        loginPage.waitUntilLoaded();
        Assert.assertTrue(loginPage.isLoaded(), "Login page should be visible after successful Sign Up");

        // Step 3: login with the newly created user
        DashboardPage dashboard = loginPage.safeLoginAsAdmin(
                user.email,
                user.password,
                Duration.ofSeconds(20)
        );

        // Basic sanity: dashboard should be loaded
        dashboard.waitUntilLoaded();
        Assert.assertTrue(dashboard.isLoaded(),
                "Dashboard should be visible after logging in with the newly created user");
    }


}
