(function(exports){
    var OTDCBWHomeHeaderActions = exports.OTDCBWHomeHeaderActions = function OTDCBWHomeHeaderActions(){};

    /**
	 * This function creates toolbar level integration point in Home View
	 */
	otui.OTDCBWHomeHeaderActions = new otui.IntegrationPoint("OTDCBWHomeHeaderActions", otui.is(otui.modes.TABLET) ? "mobileactions" : "gallery-view-action",
	{
		'render' : otui.IntegrationPoint.OVERFLOW({"maxItems" : 4, 'in-menu' : otui.IntegrationPoint.OVERFLOW_IN_MENU.SHOW_OVERFLOWED}),
		'menu' : {
			'text' :
				{
				'desktop' : otui.tr('More'),
				'touch' : '...'
				},
			'template' : 'gallery-view-action-more'
		}
	});



})(window);