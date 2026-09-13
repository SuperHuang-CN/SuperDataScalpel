package cn.superhuang.data.scalpel.business.dsh.web;

import cn.superhuang.data.scalpel.business.dsh.config.DshProperties;
import cn.superhuang.data.scalpel.business.dsh.service.DshAttachmentService;
import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;

@Component
public class DshRequestBoundaryFilter extends OncePerRequestFilter {
    private final DshProperties properties;
    private final ProblemDetailWriter errors;
    public DshRequestBoundaryFilter(DshProperties properties, ProblemDetailWriter errors) { this.properties=properties;this.errors=errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/v1/dsh/") || !request.getMethod().equals("POST");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        int limit=path.matches("/api/v1/dsh/sessions/[a-fA-F0-9-]{36}/attachments")
                ? DshAttachmentService.MAX_UPLOAD_REQUEST_BYTES : properties.getMaxRequestBytes();
        if(request.getContentLengthLong()>limit) { errors.write(request,response,ProblemType.PAYLOAD_TOO_LARGE,"DSH 请求超过大小限制。");return; }
        byte[] body=request.getInputStream().readNBytes(limit+1);
        if(body.length>limit) { errors.write(request,response,ProblemType.PAYLOAD_TOO_LARGE,"DSH 请求超过大小限制。");return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public int read(byte[] b,int offset,int length) { return input.read(b,offset,length); }
                    public boolean isFinished() { return input.available()==0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new IllegalStateException("DSH command body uses synchronous MVC binding"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
        },response);
    }
}
