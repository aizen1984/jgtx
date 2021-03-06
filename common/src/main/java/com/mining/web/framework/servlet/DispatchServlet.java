/**
 * File Name: DispatchServlet.java
 * Author: 
 * Created Time: 2019-02-20
 */

package com.mining.web.framework.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mining.web.framework.bean.Data;
import com.mining.web.framework.bean.Handler;
import com.mining.web.framework.bean.Param;
import com.mining.web.framework.bean.View;
import com.mining.web.framework.helper.BeanHelper;
import com.mining.web.framework.helper.ConfigHelper;
import com.mining.web.framework.helper.ControllerHelper;
import com.mining.web.framework.helper.HelperLoader;
import com.mining.web.framework.helper.RequestHelper;
import com.mining.web.framework.helper.UploadHelper;
import com.mining.web.framework.util.JsonUtil;
import com.mining.web.framework.util.ReflectionUtil;
import com.mining.web.framework.util.StringUtil;

/**
 * class: DispatchServlet
 * desc: 请求转发器
 */
@WebServlet(urlPatterns = "/", loadOnStartup = 0)
public class DispatchServlet extends HttpServlet {
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        HelperLoader.init();

        ServletContext servletContext = servletConfig.getServletContext();

        // 注册处理 JSP 的Servlet
        ServletRegistration jspServlet = servletContext.getServletRegistration("jsp");
        jspServlet.addMapping(ConfigHelper.getAppJspPath() + "*");

        // 注册处理静态资源的默认Servlet
        ServletRegistration defaultServlet = servletContext.getServletRegistration("default");
        defaultServlet.addMapping(ConfigHelper.getAppAssetPath() + "*");

        UploadHelper.init(servletContext);
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 获取请求方法与路径
        String requestMethod = request.getMethod().toLowerCase();
        String requestPath = request.getPathInfo();

        if (requestPath.equals("/favicon.ico")) {
            return;
        }

        // 获取Action处理器
        Handler handler = ControllerHelper.getHandler(requestMethod, requestPath);

        if (handler != null) {
            Class<?> controllerClass = handler.getControllerClass();
            Object controllerBean = BeanHelper.getBean(controllerClass);

            Param param;
            if (UploadHelper.isMultipart(request)) {
                param = UploadHelper.createParam(request);
            } else {
                param = RequestHelper.createParam(request);
            }
            Object result;

            // 调用Action方法
            Method actionMethod = handler.getActionMethod();
            if (param.isEmpty()) {
                result = ReflectionUtil.invokeMethod(controllerBean, actionMethod);
            } else {
                result = ReflectionUtil.invokeMethod(controllerBean, actionMethod, param);
            }

            // 处理 Action 方法返回值
            if (result instanceof View) {
                handleViewResult((View) result, request, response);
                // 返回 JSP 页面
                
            } else if (result instanceof Data) {
                handleDataResult((Data) result, response);
            }
        }
    }

    private void handleViewResult(View view, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = view.getPath(); 
        if (StringUtil.isNotEmpty(path)) {
            if (path.startsWith("/")) {
                response.sendRedirect(request.getContextPath() + path);
            } else {
                Map<String, Object> model = view.getModel();
                for (Map.Entry<String, Object> entry : model.entrySet()) {
                    request.setAttribute(entry.getKey(), entry.getValue());
                }
                request.getRequestDispatcher(ConfigHelper.getAppJspPath() + path).forward(request, response);
            }
        }
    }

    private void handleDataResult(Data data, HttpServletResponse response) throws IOException {
        Object model = data.getModel();
        
        if (model != null) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter writer = response.getWriter();
            String json = JsonUtil.toJson(model);
            writer.write(json);
            writer.flush();
            writer.close();
        }
    }
}
