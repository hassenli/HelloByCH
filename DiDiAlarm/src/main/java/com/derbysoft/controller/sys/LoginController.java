package com.derbysoft.controller.sys;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.derbysoft.dao.sys.UserDao;
import com.derbysoft.entity.sys.SYS_Module;
import com.derbysoft.entity.sys.SYS_ModuleButt;
import com.derbysoft.entity.sys.SYS_User;
import com.derbysoft.form.UserForm;
import com.derbysoft.jms.activemq.JpushSender;
import com.derbysoft.redis.service.RedisService;
import com.derbysoft.redis.service.UserService;
import com.derbysoft.util.CookieUtils;
import com.derbysoft.util.TreeHelper;

import dy.hrtworkframe.controller.base.BaseController;
import dy.hrtworkframe.entity.User;
import dy.hrtworkframe.exception.CustomerException;
import dy.hrtworkframe.util.Const;
import dy.hrtworkframe.util.MD5;
import dy.hrtworkframe.util.MessageUtil;
import dy.hrtworkframe.util.Sha1Util;

@Transactional
@Controller
@RequestMapping("login.do")
public class LoginController extends BaseController {

	private static String IS_LOGIN = "isLogin";
	private static String IS_ERRORLOGIN = "isErrorLogin";
	private static String IS_VALIDATION = "isValidation";
	private static String DD_USER_TOKEN = "DD_USER_TOKEN";
	private static final Integer COOKIE_TIME = 60 * 60 * 24 * 7;
	@Autowired
	public UserDao userDao;

	@Autowired
	public UserService userService;

	@Autowired
	private JpushSender jpushSender;
	@Autowired
	private 	RedisService redisService;
	/**
	 * 打开主页或者请选择您的使用账户类型界面
	 */
	@RequestMapping(params = "p=loginIndex")
	public ModelAndView loginIndex() {
		pd = this.getPageData();
		mv.addObject("model", pd);
		mv.setViewName("/system/home/index");
		return mv;
	}

/*	@RequestMapping(params = "p=loginIndex")
	public ModelAndView loginIndex(HttpServletRequest request  ) {
		pd = this.getPageData();
		mv.addObject("model", pd);
		mv.setViewName("/system/home/index");
		
		
		String cookie = CookieUtils.getCookieValue(request, "user");
		if(null==cookie){
			return mv;	
			
		}else{
		String string = redisService.get(cookie);
			if(null==string){
				return mv;
			}
			else{
				mv.addObject("name", string);
			}
		}
		
		return mv;
	
	}*/
	
	
	
	
	@RequestMapping(params = "p=loginValidation")
	public @ResponseBody Map<String, Object> loginValidation(
			HttpServletRequest request, HttpServletResponse response,
			@RequestParam String userName, @RequestParam String password,
			@RequestParam String validationCode) {
		Map<String, Object> map = new HashMap<String, Object>();
		pd = this.getPageData();
		SYS_User user = null;
		try {
//			userService.setRedisTodayCall();
			// String userValidationCode = ((String)
			// session.getAttribute(Const.SESSION_SECURITY_CODE)).toLowerCase();
			UserForm userForm = new UserForm(userName);

			user = userDao.get(userForm);
			if (!user.getPassword().equals(Sha1Util.getSha1(MD5.md5(password)))) {
				throw new CustomerException("用户名或者密码错误！");
			}

			userForm.setUser(user);
			map.put("status", IS_LOGIN);
			map.put("errInfo", "登陆成功！");
			// 代码抽取
//			this.setRedisUser(request, response, user);
//			jpushSender.send("xxxxxxx");
			if ((user != null && user.getPassword().equals(Sha1Util.getSha1(MD5.md5(password))))
					&& validationCode.endsWith(validationCode.toLowerCase())) {

				map.put("status", IS_LOGIN);
				map.put("errInfo", "登陆成功！");
				this.setRedisUser(request, response, user);
			} else if (user != null && user.getPassword().equals(Sha1Util.getSha1(MD5.md5(password)))) {
				map.put("status", IS_VALIDATION);
				map.put("errInfo", "验证码有误！");
			} else {
				map.put("status", IS_ERRORLOGIN);
				map.put("errInfo", "账号或者密码错误！");
			}
		} catch (Exception e) {
			return MessageUtil.exception(user, e);
		}
		return map;
	}

	@RequestMapping(params = "p=loginApp", method = RequestMethod.POST)
	public ModelAndView login(HttpServletRequest request,
			HttpServletResponse response, HttpSession session,
			UserForm userForm) {
		// 我们已经把user写到了cookie中这时,我们值需要从cookie中拿到user_key不需要查询数据库
		// 这里可能还需要判断,防止redis宕机,以及用户使用其他用户登录
		SYS_User user = null;
		try {

			user = userService.queryUserByToken(CookieUtils.getCookieValue(
					request, DD_USER_TOKEN));
			if (user == null
					//|| user.getUserName() != userDao.get(userForm).getUserName()
					) {
				user = userDao.get(userForm);
			}
			pd = this.getPageData();

			if (user != null) {
				// 改造session,将这些常量通过redis共享
				List<SYS_ModuleButt> button = userDao.selectUserButton(user);
				session.setAttribute(Const.SESSION_USER, user);
				session.setAttribute(Const.SESSION_MENU_LIST_BUTTON,  button);
				
				List<SYS_Module> modules = userDao.selectUserModule(user);
//				List<SYS_ModuleButt> buttonAll = userDao.selectAllButton();
				// 标识出那些子系统需要显示
				
				modules.add(createRootModule());
				TreeHelper tree = new TreeHelper(
						TreeHelper.changeEnititiesToTreeNodes(modules));
				session.setAttribute(Const.SESSION_MENU_LIST, tree.getRoot());
				mv.addObject("model", pd);
				mv.setViewName("redirect:login.do?p=loginIndex");
				return mv;
			}
			mv.setViewName("/system/home/login");

		} catch (Exception e) {
			// TODO Auto-generated catch block
			return MessageUtil.exception(user, mv, e);
		}
		return MessageUtil.success(mv);
	}

	private SYS_Module createRootModule() {
		SYS_Module root = new SYS_Module();
		root.setAppPlatform("根节点");
		root.setModuleID("0");
		root.setModuleName("root");
		root.setShowIndex("0");
		return root;
	}

	public void setRedisUser(HttpServletRequest request,
			HttpServletResponse response, SYS_User user) {
		// 判断redis中是否已经存在,存在就直接拿,redis中是否是其他帐号
		try {
			if (userService.queryUserByToken(CookieUtils.getCookieValue(
					request, DD_USER_TOKEN)) == null
					|| !user.getUserID().equals(
							userService.queryUserByToken(CookieUtils
									.getCookieValue(request, DD_USER_TOKEN)).getUserID())) {
				// redis项目改造,将user写入到redis中,userkey写到cookie中
				String token = userService.doLoginToRedis(user);
				// 设置token在cookie的存活时间,这里没有设置,浏览器关闭,cookie失效
				CookieUtils.setCookie(request, response, DD_USER_TOKEN, token,
						COOKIE_TIME);
			} else {
				// 重新设置redis的存活时间
				userService.setRedisUserTime(CookieUtils.getCookieValue(
						request, DD_USER_TOKEN));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 根据token查询用户信息
	 * 
	 * @param token
	 * @return
	 */
	@RequestMapping(value = "p=queryByToken", method = RequestMethod.GET)
	public ResponseEntity<User> queryUserByToken(
			@PathVariable("token") String token) {
		try {
			User user = this.userService.queryUserByToken(token);
			if (user == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
			}
			return ResponseEntity.ok(user);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				null);
	}
}
