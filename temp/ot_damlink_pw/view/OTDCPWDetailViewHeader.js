(function(exports)
{	
	var OTDCPWDetailViewHeader = exports.OTDCPWDetailViewHeader = function OTDCPWDetailViewHeader(){};
	/**
	 * This function will invoke create Workspace dialog.
	 * @param event
	 */
	OTDCPWDetailViewHeader.uploadAsset = function(event)
	{
		AssetUploadDialogView.show(event,undefined,[]);
	};

	/**
	 * This function creates toolbar level integration point in Detail View
	 */
	otui.OTDCPWDetailViewHeader = new otui.IntegrationPoint("OTDCPWDetailViewHeader", otui.is(otui.modes.TABLET) ? "mobileactions" : "gallery-view-action",
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
	
	/**
	 * This function validates if user has role to create workspace
	 */
	OTDCPWDetailViewHeader.setupUploadAsset = function(event, resource, point)
	{		
		var view = AssetActions.getCurrentView();
		var parent = view.internalProperties.parentView.internalProperties.parentView;
		return otui.UserFETManager.isTokenAvailable("IMPORT") && OTDCPWAssetsView.setUPUploadAssets.call(view,parent);		
	};

	/**
	 * This function will register New workspace button in the header bar of Home View.
	 * This is called in html page of Home view 
	 */
	otui.ready(function() {
		otui.OTDCPWDetailViewHeader.register({
			'name' : 'Upload Assets', 
			'text' : otui.tr('Upload Assets'), 
			'img' : {
				desktop: '../../style/img/action_addto_collection24.svg', 
				tablet: '../../style/img/action_addto_collection24.svg', 
				phone: '../../style/img/action_addto_collection24.svg'
			}, 
			'setup' : OTDCPWDetailViewHeader.setupUploadAsset, 
			'select' : OTDCPWDetailViewHeader.uploadAsset
		}, 1);
	});
})(window);