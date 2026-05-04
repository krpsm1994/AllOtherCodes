package self.sai.stock.AlgoTrading.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all non-API, non-static requests to index.html so Angular's
 * HTML5 router can handle /login, /dashboard, etc. on page refresh.
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = {"/", "/login", "/dashboard"})
    public String forward(HttpServletRequest request) {
        return "forward:/index.html";
    }
}
