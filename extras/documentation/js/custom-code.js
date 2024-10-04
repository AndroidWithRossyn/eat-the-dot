$( document ).ready(function() {
	//console.log( "ready!" );
	console.log(configProperties.itemType);
	$("#mysite").attr("href",configProperties.marketPlace);
	$("#mysite2").attr("href",configProperties.marketPlace);
	$("#mysite2").html('<a href="https://wa.me/message/TYB4IQS7VAHDC1"><strong>Chat to get discount on other items</strong></a>');
	$("#contactMe").attr("href",configProperties.contactMe);
	$("#contactMe2").attr("href",configProperties.contactMe);
	$("#contactMe").text(configProperties.contactLabel);
	$("#authorMail").attr("href","mailto:" + configProperties.authorMail);
	$("#authorMail").text(configProperties.authorMail);
	$("#authorSkype").attr("href","skype:" + configProperties.authorSkype + "?chat");
	$("#authorSkype").text(configProperties.authorSkype);
	$("#authorName").text(configProperties.authorName);
	$("#createDate").text(configProperties.createDate);
	$("#updateDate").text(configProperties.updateDate);
	$("#scriptType").text(configProperties.itemType);
	$("#packageName").text("http://play.google.com/store/apps/details?id=" + configProperties.gamePackage);
	$("#hashtag").text(configProperties.gameHashtag);
	$("#ourReskinMail").attr("href","mailto:" + configProperties.authorMail);
	$("#ourReskinSkype").attr("href","skype:" + configProperties.authorSkype + "?chat");
	$(".gameName").text(configProperties.gameName);
	$(".companyName").text(configProperties.companyName);
	$(".adType").text(configProperties.itemType);
	$(".sdkVersion").text(configProperties.sdkVersion);
	$(".buildTools").text(configProperties.buildTools);

		
	if(configProperties.itemType == "facebook"){
		$("#scriptTitle").text(configProperties.itemTitleNameFb);
		$("#admobversion").remove();
		$("#admob").remove();
	}
	if(configProperties.itemType == "admob"){
		$("#scriptTitle").text(configProperties.itemTitleNameAdmob);
		$("#fbversion").remove();
		$("#facebook").remove();
	}
			
});