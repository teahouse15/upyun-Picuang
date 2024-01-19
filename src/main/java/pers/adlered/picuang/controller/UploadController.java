package pers.adlered.picuang.controller;

import com.upyun.Params;
import com.upyun.RestManager;
import com.upyun.UpException;
import okhttp3.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import pers.adlered.picuang.access.HttpOrHttpsAccess;
import pers.adlered.picuang.log.Logger;
import pers.adlered.picuang.prop.Prop;
import pers.adlered.picuang.result.Result;
import pers.adlered.picuang.tool.IPUtil;
import pers.adlered.picuang.tool.ToolBox;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class UploadController {
    public static SimpleCurrentLimiter uploadLimiter = new SimpleCurrentLimiter(1, 1);
    public static SimpleCurrentLimiter cloneLimiter = new SimpleCurrentLimiter(3, 1);

    /**
     * 携带管理员密码进行图片上传
     * @param file
     * @param request
     * @param password
     * @return
     */
    @RequestMapping("/upload/auth")
    @ResponseBody
    public Result uploadInAuth(@PathVariable MultipartFile file, HttpServletRequest request, HttpSession session, String password) {
        String truePassword = Prop.get("password");
        Result result = new Result();
        if (!adminOnly(session, result)) {
            return upload(file, request);
        } else {
            if (truePassword.equals(password)) {
                return upload(file, request);
            }
        }
        result.setCode(500);
        result.setMsg("Invalid password.");
        return result;
    }

    @RequestMapping("/upload")
    @ResponseBody
    public Result upload(@PathVariable MultipartFile file, HttpServletRequest request, HttpSession session) {
        Result result = new Result();
        if (adminOnly(session, result)) {
            result.setCode(500);
            result.setMsg("Admin only upload.");
            return result;
        }


        return upload(file, request);
    }

    public Result upload(@PathVariable MultipartFile file, HttpServletRequest request) {
        synchronized (this) {
            uploadLimiter.setExpireTimeMilli(500);
            String addr = IPUtil.getIpAddr(request).replaceAll("\\.", "/").replaceAll(":", "/");
            boolean allowed = uploadLimiter.access(addr);
            try {
                while (!allowed) {
                    allowed = uploadLimiter.access(addr);
                    System.out.print(".");
                    Thread.sleep(100);
                }
            } catch (InterruptedException IE) {
            }
            Result result = new Result();
            //是否上传了文件
            if (file.isEmpty()) {
                result.setCode(406);
                return result;
            }
            //是否是图片格式
            String filename = file.getOriginalFilename();
            String suffixName = ToolBox.getSuffixName(filename);
            Logger.log("SuffixName: " + suffixName);
            if (ToolBox.isPic(suffixName)) {
                // 上传

                Map<String, String> paramsMap = new HashMap<String, String>();
                try {

                    String upyunFilename = RandomStringUtils.randomAlphanumeric(3) + "_pic" + suffixName;
                    RestManager upyunRestManager = new RestManager(Prop.get("bucketName"), Prop.get("userName"), Prop.get("userPasswd"));
                    upyunRestManager.setApiDomain(RestManager.ED_AUTO);

                    // 获取当前日期 LocalDate.now().toString() 2021-05-26 [0]=2021 [1]=05 [2]=26
                    String nowDate[] = LocalDate.now().toString().split("-");
                    String dateDir = "/" + nowDate[0] + "/" + nowDate[1] + "-" + nowDate[2] + "/";
                    if (!upyunRestManager.mkDir(dateDir).isSuccessful()) {
                        Logger.log(Level.INFO + "Directory creation failed [path=" + dateDir + "]");
                    }
                    String filePath = dateDir + upyunFilename;

                    Response response = upyunRestManager.writeFile(filePath, file.getInputStream(), paramsMap);
                    // Response{protocol=http/1.1, code=200, message=OK, url=https://v0.api.upyun.com/image-myjinji/test.jpg}
                    if (200 == response.code()) {
                        result.setCode(200);
                        result.setMsg(Prop.get("website") + "/" + filePath);
                    }


                } catch (IOException | UpException e) {
                    result.setCode(500);
                    result.setMsg("未知错误。");
                    return result;
                }

                String time = ToolBox.getDirByTime();
                result.setData(filename);
//                Logger.log("Saving into " + dest.getAbsolutePath());
                String url = "/uploadImages/" + addr + "/" + time + filename;
                result.setCode(200);
                result.setMsg(url);
                return result;
            } else {
                result.setCode(500);
                result.setMsg("不是jpg/jpeg/png/svg/gif图片！");
                return result;
            }
        }
    }

    @RequestMapping("/clone")
    @ResponseBody
    public Result clone(String url, HttpServletRequest request, HttpSession session) {
        synchronized (this) {
            String addr = IPUtil.getIpAddr(request).replaceAll("\\.", "/").replaceAll(":", "/");
            boolean allowed = cloneLimiter.access(addr);
            try {
                while (!allowed) {
                    allowed = cloneLimiter.access(addr);
                    System.out.print(".");
                    Thread.sleep(100);
                }
            } catch (InterruptedException IE) {
            }
            Result result = new Result();
            if (adminOnly(session, result)) {
                return result;
            }
            try {
                String suffixName = ToolBox.getSuffixName(url);
                Logger.log("SuffixName: " + suffixName);
                String time = ToolBox.getDirByTime();
                File dest;
                if (ToolBox.isPic(suffixName)) {
                    dest = ToolBox.generatePicFile(suffixName, time, addr);
                } else {
                    dest = ToolBox.generatePicFile(".png", time, addr);
                }
                Logger.log("Saving into " + dest.getAbsolutePath());
                if (!dest.getParentFile().exists()) {
                    dest.getParentFile().mkdirs();
                }
                FileOutputStream fileOutputStream = new FileOutputStream(dest);
                BufferedInputStream bufferedInputStream = HttpOrHttpsAccess.post(url,
                        "",
                        null);
                byte[] bytes = new byte[1024];
                int len;
                while ((len = bufferedInputStream.read(bytes)) != -1) {
                    fileOutputStream.write(bytes, 0, len);
                }
                fileOutputStream.flush();
                fileOutputStream.close();
                bufferedInputStream.close();
                Pattern p = Pattern.compile("(?<=http://|\\.)[^.]*?\\.(com|cn|net|org|biz|info|cc|tv)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = p.matcher(url);
                matcher.find();
                result.setData("From " + matcher.group());
                result.setCode(200);
                result.setMsg("/uploadImages/" + addr + "/" + time + dest.getName());
                int count = Integer.parseInt(Prop.get("imageUploadedCount"));
                ++count;
                Prop.set("imageUploadedCount", String.valueOf(count));
                return result;
            } catch (Exception e) {
                result.setCode(500);
                result.setMsg(e.getClass().toGenericString());
                return result;
            }
        }
    }

    private boolean adminOnly(HttpSession session, Result result) {
        if (Prop.get("adminOnly").equals("on")) {
            Logger.log("AdminOnly mode is on! Checking user's permission...");
            if (!logged(session)) {
                Logger.log("User not logged! Uploading terminated.");
                result.setCode(401);
                result.setMsg("管理员禁止了普通用户上传文件！");
                return true;
            }
            Logger.log("Admin is uploading...");
        }
        return false;
    }

    /**
     * 检查管理员是否已登录
     *
     * @param session
     * @return
     */
    private boolean logged(HttpSession session) {
        boolean logged = false;
        try {
            logged = Boolean.parseBoolean(session.getAttribute("admin").toString());
        } catch (NullPointerException NPE) {
        }
        return logged;
    }
}
