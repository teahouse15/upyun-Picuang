package pers.adlered;

import com.upyun.RestManager;
import com.upyun.UpException;
import okhttp3.Response;

import java.io.IOException;

public class Test {

    public static void main(String[] args) throws UpException, IOException {
        RestManager manager = new RestManager("image-myjinji", "bogendihong", "lxoIAgmr6dCE0bBNhIEQKwsxXsKI6jCf");
        manager.setApiDomain(RestManager.ED_AUTO);

        String path = "/";
        // 获取目录中文件列表
        Response response = manager.readDirIter(path,null);
        System.out.println(response.body().string());
    }
}
