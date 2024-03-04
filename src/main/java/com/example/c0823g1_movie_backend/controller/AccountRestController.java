package com.example.c0823g1_movie_backend.controller;

import com.example.c0823g1_movie_backend.dto.*;
import com.example.c0823g1_movie_backend.dto.AccountDTO;
import com.example.c0823g1_movie_backend.dto.AccountStatisticDTO;
import com.example.c0823g1_movie_backend.dto.IAccountDTO;
import com.example.c0823g1_movie_backend.model.Account;
import com.example.c0823g1_movie_backend.model.ChangePasswordDto;
import com.example.c0823g1_movie_backend.model.LoginSuccess;
import com.example.c0823g1_movie_backend.service.IAccountService;
import com.example.c0823g1_movie_backend.service.IRoleService;
import com.example.c0823g1_movie_backend.service.JwtService;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.security.Principal;
import java.util.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/account")
public class AccountRestController {
    @Autowired
    private JwtService jwtService;
    @Autowired
    private IAccountService iAccountService;
    @Autowired
    private IRoleService iRoleService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String KEY_API_GOOGLE_APP = "AIzaSyC_s2yYtCEqi0h5NenIr7zd_qZITfKoKlI";
    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: Receive account information (accountName and password) and check account information
     * @return HttpStatus.BAD_REQUEST if account not found/ access token,role user, account information and HttpStatus.OK if account information is accurate/
     * HttpStatus.INTERNAL_SERVER_ERROR if server error
     */
    @PostMapping("/login")
    public ResponseEntity<LoginSuccess> login(HttpServletRequest request, @RequestBody AccountDTO accountDTO) {
        String accessToken = "";
        String roleUser = "";
        HttpStatus httpStatus = null;
        LoginSuccess loginSuccess;
        Account account = new Account();
        BeanUtils.copyProperties(accountDTO, account);
        try {
            if (iAccountService.checkLogin(account)) {
                roleUser = iAccountService.getRoleUser(account);
                accessToken = jwtService.generateTokenLogin(account.getAccountName());
                Optional<IAccountDTO> iAccountDTO = iAccountService.findByAccountName(account.getAccountName());
                if (!iAccountDTO.isPresent()) {
                    httpStatus = HttpStatus.BAD_REQUEST;
                    return new ResponseEntity<>(httpStatus);
                }
                loginSuccess = new LoginSuccess(accessToken, roleUser, iAccountDTO.get());
                httpStatus = HttpStatus.OK;
                return new ResponseEntity<LoginSuccess>(loginSuccess, httpStatus);
            } else {
                httpStatus = HttpStatus.BAD_REQUEST;
                return new ResponseEntity<>(httpStatus);
            }
        } catch (Exception ex) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return new ResponseEntity<>(httpStatus);
    }

    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: receive accessToken code returned to facebook. Use that accessToken code to call Facebook to get user information. Check account information.
     * If account not found create new account.
     * @return HttpStatus.BAD_REQUEST if account has been locked/ access token,role user, account information and HttpStatus.OK if account information is accurate/
     * HttpStatus.INTERNAL_SERVER_ERROR if server error
     */
    @PostMapping("/login-by-fb")
    public ResponseEntity<LoginSuccess> loginByFacebook(HttpServletRequest request, @RequestBody TokenDTO tokenDTO) {
        String accessToken = "";
        String roleUser = "";
        Account account;
        LoginSuccess loginSuccess;
        HttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet request1 = new HttpGet("https://graph.facebook.com/v13.0/me?fields=id,name,email,picture&access_token=" + tokenDTO.getValue());
            HttpResponse response = httpClient.execute(request1);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String result = EntityUtils.toString(entity);
                Gson gson = new Gson();
                FacebookDTO facebookDTO = gson.fromJson(result, FacebookDTO.class);
                account = convertAccount(facebookDTO);
                if (iAccountService.checkLoginByFB(account)) {
                    roleUser = iAccountService.getRoleUserFB(account);
                    accessToken = jwtService.generateTokenLogin(account.getAccountName());
                    Optional<IAccountDTO> iAccountDTO = iAccountService.findByFacebookId(account.getFacebookId());
                    if (!iAccountDTO.isPresent()) {
                        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                    }
                    if (iAccountDTO.get().getIsDeleted()) {
                        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                    }
                    loginSuccess = new LoginSuccess(accessToken, roleUser, iAccountDTO.get());
                    return new ResponseEntity<LoginSuccess>(loginSuccess, HttpStatus.OK);
                } else {
                    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                }
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Account convertAccount(FacebookDTO facebookDTO) {
        Account account = new Account();
        account.setFacebookId(facebookDTO.getId());
        account.setEmail(facebookDTO.getEmail());
        account.setFullName(facebookDTO.getName());
        account.setProfilePicture(facebookDTO.getPicture().getData().getUrl());
        createAccountFB(account);
        return account;
    }

    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: receive oauthAccessToken code returned to google. Use that accessToken code to call Google to get user information. Check account information.
     * If account not found create new account.
     * @return HttpStatus.BAD_REQUEST if account has been locked/ access token,role user, account information and HttpStatus.OK if account information is accurate/
     * HttpStatus.INTERNAL_SERVER_ERROR if server error
     */
    @PostMapping("/login-by-gg")
    public ResponseEntity<LoginSuccess> loginByGoogle(HttpServletRequest request, @RequestBody TokenDTO tokenDTO) {
        String accessToken = "";
        String roleUser = "";
        LoginSuccess loginSuccess;
        Account account;
        try {
            HttpTransport httpTransport = new NetHttpTransport();
            HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
            String apiUrl = "https://people.googleapis.com/v1/people/me?personFields=names,emailAddresses,photos&key=" + KEY_API_GOOGLE_APP;
            GenericUrl url = new GenericUrl(apiUrl);
            HttpRequest request1 = requestFactory.buildGetRequest(url);
            request1.getHeaders().setAuthorization("Bearer " + tokenDTO.getValue());
            com.google.api.client.http.HttpResponse response = request1.execute();
            String responseBody = response.parseAsString();
            System.out.println(responseBody);
            account = fromJson(responseBody);
            if (iAccountService.checkLoginByGg(account)) {
                roleUser = iAccountService.getRoleUserGG(account);
                accessToken = jwtService.generateTokenLogin(account.getAccountName());
                Optional<IAccountDTO> iAccountDTO = iAccountService.findByGoogleID(account.getGoogleId());
                if (!iAccountDTO.isPresent()) {
                    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                }
                if (iAccountDTO.get().getIsDeleted()) {
                    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                }
                loginSuccess = new LoginSuccess(accessToken, roleUser, iAccountDTO.get());
                return new ResponseEntity<LoginSuccess>(loginSuccess, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Account fromJson(String jsonResponse) {
        Account account = new Account();
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
        String displayName = "";
        String photoUrl = "";
        String emailAddress = "";
        String resourceName = "";
        JsonArray namesArray = jsonObject.getAsJsonArray("names");
        if (namesArray != null && !namesArray.isEmpty()) {
            JsonObject nameObject = namesArray.get(0).getAsJsonObject();
            displayName = nameObject.get("displayName").getAsString();
        }
        JsonArray photosArray = jsonObject.getAsJsonArray("photos");
        if (photosArray != null && !photosArray.isEmpty()) {
            JsonObject photoObject = photosArray.get(0).getAsJsonObject();
            photoUrl = photoObject.get("url").getAsString();
        }
        JsonArray emailArray = jsonObject.getAsJsonArray("emailAddresses");
        if (emailArray != null && !emailArray.isEmpty()) {
            JsonObject emailObject = emailArray.get(0).getAsJsonObject();
            emailAddress = emailObject.get("value").getAsString();
        }
        resourceName = jsonObject.get("resourceName").getAsString();
        account.setEmail(emailAddress);
        account.setGoogleId(resourceName);
        account.setFullName(displayName);
        account.setProfilePicture(photoUrl);
        createAccountGG(account);
        return account;
    }

    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: Receive account information (email) and check account information.
     * If account found create an automatic password and update the account then send a notification email to the user.
     * @return HttpStatus.BAD_REQUEST if account not found/ HttpStatus.OK if account is found
     */
    @PostMapping("/forget-password")
    public ResponseEntity<?> forgetPassword(HttpServletRequest request, @RequestBody AccountDTO accountDTO) {
        Account account = new Account();
        BeanUtils.copyProperties(accountDTO, account);
        System.out.println(account.getEmail());
        Optional<IAccountDTO> iAccountDTO = iAccountService.findByEmail(account.getEmail());
        if (!iAccountDTO.isPresent()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        String newPassword = generateRandomString();
        iAccountService.updatePasswordAndSendMail(iAccountDTO.get().getId(), newPassword);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: Receive account information (email and password) and check account information
     * @return HttpStatus.BAD_REQUEST if account not found/ access token,role user, account information and HttpStatus.OK if account information is accurate/
     * HttpStatus.INTERNAL_SERVER_ERROR if server error
     */
    @PostMapping("/login-email")
    public ResponseEntity<LoginSuccess> loginByEmail(HttpServletRequest request, @RequestBody AccountDTO accountDTO) {
        Account account = new Account();
        BeanUtils.copyProperties(accountDTO, account);
        String accessToken = "";
        String roleUser = "";
        LoginSuccess loginSuccess;
        HttpStatus httpStatus = null;
        try {
            if (iAccountService.checkLoginByEmail(account)) {
                roleUser = iAccountService.getRoleUserEmail(account);
                Optional<IAccountDTO> iAccountDTO = iAccountService.findByEmail(account.getEmail());
                if (!iAccountDTO.isPresent()) {
                    httpStatus = HttpStatus.BAD_REQUEST;
                    return new ResponseEntity<>(httpStatus);
                }
                accessToken = jwtService.generateTokenLogin(iAccountDTO.get().getAccountName());
                loginSuccess = new LoginSuccess(accessToken, roleUser, iAccountDTO.get());
                httpStatus = HttpStatus.OK;
                return new ResponseEntity<LoginSuccess>(loginSuccess, httpStatus);
            } else {
                httpStatus = HttpStatus.BAD_REQUEST;
                return new ResponseEntity<>(httpStatus);
            }
        } catch (Exception ex) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return new ResponseEntity<>(httpStatus);
    }

    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: Receive account information and check account information
     * If account not found create new account.
     */
    private void createAccountGG(Account account) {
        if (!iAccountService.checkLoginByGg(account)) {
            account.setAccountName(account.getEmail());
            iAccountService.register(account);
        }
    }

    /* Create by: TuanTA
     * Date created: 29/02/2024
     * Function: Register New account
     * @Return HttpStatus.BAD_REQUEST If the account creation information is wrong with the format / HttpStatus.OK If the data fields are correct
     */
    @PostMapping("/register")
    public ResponseEntity<?> createAccount(@RequestBody @Valid AccountDTO accountDTO , BindingResult bindingResult){
        List<String> listError = new ArrayList<>();
        if (bindingResult.hasFieldErrors()){
            return  new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }else {
            if (iAccountService.findAccountByEmail(accountDTO.getEmail()) == null){
                      listError.add("Email Đã Tồn Tại");
            }
            if (iAccountService.findAccountByPhone(accountDTO.getPhoneNumber()) == null){
                listError.add(" Số Điện Thoại Đã Tồn Tại ");
            }
            if (iAccountService.findAccountByAccountName(accountDTO.getAccountName()) == null){
                listError.add("Tài Khoản Đã Tồn Tại");
            }
            if (listError.size() > 0){
                return new ResponseEntity<>(listError,HttpStatus.BAD_REQUEST);
            }


            String to = accountDTO.getEmail();
            String subject = "[C0823G1-Cinema]-Phản hồi yêu cầu cấp lại mật khẩu tài khoản";
            String templateName = "email-register";
            org.thymeleaf.context.Context context = new  org.thymeleaf.context.Context();
            String randomCode = RandomStringUtils.random(6,true,true);
            System.out.println(randomCode);
            context.setVariable("fullName",accountDTO.getFullName());
            context.setVariable("account",accountDTO.getAccountName());
            context.setVariable("password",accountDTO.getPassword());
            context.setVariable("randomCode",randomCode);
            iAccountService.sendEmailWithHtmlTemplate(to,subject,templateName,context);
            if (!accountDTO.getVerificationCode().equals(randomCode)){
                listError.add("Mã Xác Nhận không đúng");
            }
            String encode = passwordEncoder.encode(accountDTO.getPassword());
            Account account = new Account();
            BeanUtils.copyProperties(accountDTO,account);
            account.setPassword(encode);
            Account account1 = iAccountService.getLastUser();
            account.setPoint(0);
            account.setMemberCode(account1.getMemberCode());
            iAccountService.register(account, 2L);
            System.out.println("Success");
            return new ResponseEntity<>(account, HttpStatus.OK);
        }

    }
    /* Create by: TuanTA
     * Date created: 29/02/2024
     * Function: Show Detail User Account
     * @Return HttpStatus.NO_CONTENT if userName of User is Null , @Return HttpStatus.OK if userName of User is not Null
     */
    @GetMapping("/detailUser")
    public ResponseEntity<Account> detailAccountUser(Principal principal){
        if (principal.getName() == null){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        Account account1 = iAccountService.getAllInfoUser(principal.getName());
        return new ResponseEntity<>(account1,HttpStatus.OK);
    }
    /* Create by: TuanTA
     * Date created: 29/02/2024
     * Function: Change Infor Of User Account
     * @Return HttpStatus.BAD_REQUEST If the account creation information is wrong with the format / HttpStatus.OK If the data fields are correct
     */
    @PatchMapping("/changeInfoUser")
    public ResponseEntity<?> changeInfoUserAccount(@Valid @RequestBody AccountDTO accountDTO , BindingResult bindingResult){
        if (bindingResult.hasFieldErrors()){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }else {
            Account account = new Account();
            BeanUtils.copyProperties(accountDTO,account);
            iAccountService.updateAccount(account,account.getId());
            return new ResponseEntity<>(account,HttpStatus.OK);
        }

    }
    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: Receive account information and check account information
     * If account not found create new account.
     */
    private void createAccountFB(Account account) {
        if (!iAccountService.checkLoginByFB(account)) {
            if (Objects.equals(account.getEmail(), "")) {
                account.setAccountName(account.getFacebookId());
            } else {
                account.setAccountName(account.getEmail());
            }
            iAccountService.register(account);
        }
    }
    /**
     * Created by DuyDD
     * Date Created: 29/02/2024
     * Function: Get a list of accounts that have the highest amount of money spent
     * @return HttpStatus.NO_CONTENT if there are no account/ HttpStatus.OK if there are
     */
    @GetMapping("/statistics")
    private ResponseEntity<Page<AccountStatisticDTO>> movieStatistics(@PageableDefault(value = 10) Pageable pageable) {
        Page<AccountStatisticDTO> accountList = iAccountService.getAccountStatistic(pageable);
        if (accountList.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(accountList, HttpStatus.OK);
    }
    /* Create by: BaoNDT
     * Date created: 29/02/2024
     * Function: generated random new password
     */
    private String generateRandomString() {
        String characters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder randomString = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            int randomIndex = random.nextInt(characters.length());
            char randomChar = characters.charAt(randomIndex);
            randomString.append(randomChar);
        }
        return randomString.toString();
    }

    /* Create by: TuanTA
     * Date created: 29/02/2024
     * Function: Change Password
     * @Return HttpStatus.BAD_REQUEST If the current password is not the same as the current password input and If the new password is not the same as confirming the password
     * HttpStatus.OK if the current password is the same as the current password in the input field and the new password is the same as the new password confirmation
     */
    @PatchMapping("/changePassword")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDto changePasswordDto,BindingResult bindingResult,Principal principal){
        List<String> listErrors = new ArrayList<>();
        changePasswordDto.validate(changePasswordDto,bindingResult);
        if (bindingResult.hasErrors()){
            for (FieldError error : bindingResult.getFieldErrors()){
                listErrors.add(error.getDefaultMessage());
            }
            return new ResponseEntity<>(listErrors,HttpStatus.BAD_REQUEST);
        }
       Account account = iAccountService.findAccountByAccountName(principal.getName());
        if (account.getPassword().equals(changePasswordDto.getCurrentPassword())){
            account.setPassword(passwordEncoder.encode(changePasswordDto.getNewPassword()));
            iAccountService.updatePassword(account.getPassword(),principal.getName());
            return new ResponseEntity<>("Đổi Mật Khẩu Thành Công",HttpStatus.OK);
        }else {
            listErrors.add("Mật Khẩu Hiện Tại Không Đúng");
            return new ResponseEntity<>(listErrors,HttpStatus.BAD_REQUEST);
        }
    }

}
