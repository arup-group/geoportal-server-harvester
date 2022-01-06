<!-- Extract the app name from the URL and redirect to it -->
<%
  String fullUrl = request.getServerName();
  String appName = fullUrl.replace("uat.","").replace(".arup.com","");
  String redirectUrl = "/" + appName + "/";
  response.setStatus(response.SC_MOVED_PERMANENTLY);
  response.sendRedirect(redirectUrl);
%>