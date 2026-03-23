(function(exports)
{
	var OTDCPWHomeHeaderActions = exports.OTDCPWHomeHeaderActions = function OTDCPWHomeHeaderActions(){};
	
	/**
	 * This function will invoke create Workspace dialog.
	 * @param event
	 */
	OTDCPWHomeHeaderActions.createNewWorkspace = function(event)
	{
		var view = AssetActions.getCurrentView(event);
		var data = {}; 
		var options = { 
			'save' : {'handler' : OTDCPWWorkspaceManager.CreateWorkspace },
			'initialiseCallback' : function (dialog) {
				return function (dialog)
				{
					var dialogView = otui.Views.containing(dialog);
					if (dialogView)
					{
						dialogView.internalProperties.actionView = view;
					}
				};
			}
		};
		
		OTDCPWNewWorkspaceView.show(data, options);
	};

	/**
	 * This function creates toolbar level integration point in Home View
	 */
	otui.OTDCPWHomeHeaderActions = new otui.IntegrationPoint("OTDCPWHomeHeaderActions", otui.is(otui.modes.TABLET) ? "mobileactions" : "gallery-view-action",
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
	OTDCPWHomeHeaderActions.setupcreateNewWorkspace = function(event, resource, point)
	{
		//return otui.UserFETManager.isTokenAvailable("OTDC.PW.CREATE");
		if(otui.UserFETManager.isTokenAvailable("OTDC.PW.CREATE") && otui.UserFETManager.isTokenAvailable("OTDC.PW.VIEW") ){
			return true;
		}
		else{
			return false
		}
	};

	/**
	 * This function will register New workspace button in the header bar of Home View.
	 * This is called in html page of Home view 
	 */
	otui.ready(function() {
		otui.OTDCPWHomeHeaderActions.register({
			'name' : 'New workspace', 
			'text' : otui.tr('New workspace'), 
			'img' : {
				desktop: '../../style/img/action_addto_collection24.svg', 
				tablet: '../../style/img/action_addto_collection24.svg', 
				phone: '../../style/img/action_addto_collection24.svg'
			}, 
			'setup' : OTDCPWHomeHeaderActions.setupcreateNewWorkspace, 
			'select' : OTDCPWHomeHeaderActions.createNewWorkspace 
		}, 1);
	});
})(window);