(function (exports) {
    /**
     * @class OTDCPWResyncConfirmView
     * @mixes withContent
     * @constructor
     * @param {OTDCPWResyncConfirmView} props Properties for this view.
     * @classdesc
     *
     */
    exports.OTDCPWResyncConfirmView = otui.define("OTDCPWResyncConfirmView", ['withContent'], function () {
		this.properties = {
			'name': "OTDCPWResyncConfirmView",
			'title': otui.tr("Confirm Sync")
		};
		this._initContent = function initOTDCPWAssignToProductView(self, callback) {                
			OTDCPWResyncConfirmView.nodeID = this.properties.nodeID || this.properties.resourceID;
			var template = self.getTemplate("otdcpw-resyncconfirm-content");
			callback(template);

			//Set message
			var workspaceName = OTDCPWResyncConfirmView.dialogProperties.asset.name;
			$(".otdcpw-resyncconfirm-text span")[0].innerText = otui.tr("Syncing will overwrite some of the field values for {0} with SAP Commerce Cloud data. This cannot be undone.", workspaceName) ;				
		};
	});
			 
	OTDCPWResyncConfirmView.show = function (data, options) {
		OTDCPWResyncConfirmView.dialogProperties = {};
        options = options || {};
        data = data || {};
		//store parent view details
		OTDCPWResyncConfirmView.dialogProperties = {
			srcView: data.srcView,
			asset: data.asset
		};
		options.viewProperties = {"assetInfo":data.asset};
		
        otui.dialog("otdcpwresyncconfirmdialog", options.viewProperties);
    };
	
	OTDCPWResyncConfirmView.onClickContinue = function onClickContinue(event){	
		var view = otui.Views.containing(event.target);
		var parentView = OTDCPWResyncConfirmView.dialogProperties.srcView;
		parentView.blockContent();
        view.blockContent();
		var pkValue = OTDCPWAssetActionsManager.getMetadataFieldValue(OTDCPWResyncConfirmView.dialogProperties.asset, 'OTDC.PW.PK', 'OTDC.PW.FG.INFO');
		OTDCPWMetadataView.getProductData(pkValue,function(success,response){
			if(!response || !success){
				var error = otui.tr("No data found for sync");
				otui.NotificationManager.showNotification({
					'message' : error,
					'status' : 'error',
					'stayOpen' : true
				});
				OTDCPWSyncView.unblockViews(view,parentView);
			}else{
				syncData(view,parentView,response);
			}
		},true);
	}	

	function syncData(view,parentView,productData){
		//1. Prepare json format
		//2. Lock asset
		//3. PATCH call
		//4. Unlock asset 
		var metadata = [];
		for(var i=0;i<productData.length;i++){
            OTDCPWWorkspaceManager.generateValueforSave(productData[i].value,metadata,productData[i].id);
        }
        //Check if have any data to update
        if(metadata.length){
            var data = {'metadata': metadata};
            var json = {};
            json['edited_folder'] = {'data' : data};
            //Lock asset
            otui.services.asset.lock({'assetID' : OTDCPWResyncConfirmView.dialogProperties.srcView.internalProperties.asset.asset_id}, function(response, status, success) {
				if (success)
                    updateMetadata(view,json);
				else {
					var error = (response && response.exception_body) ? response.exception_body.message : otui.tr("Could not lock this asset.");
					otui.NotificationManager.showNotification({
						'message' : error,
						'status' : 'error',
						'stayOpen' : true
					});
                    OTDCPWSyncView.unblockViews(view,parentView);
				}
			});
        }else{
            var error = otui.tr("No data found for sync");
            otui.NotificationManager.showNotification({
                'message' : error,
                'status' : 'error',
                'stayOpen' : true
            });
            OTDCPWSyncView.unblockViews(view,parentView);
        }
	}

	var updateMetadata = function updateMetadata(self,metadata){
        var folderID = OTDCPWResyncConfirmView.dialogProperties.srcView.internalProperties.asset.asset_id;
        var parentView = OTDCPWResyncConfirmView.dialogProperties.srcView;
        var assetType = "folder";
        otui.services[assetType].update({'assetID' : folderID, 'modifiedData' : metadata}, function(response, status, success) {
            // There may be scenarios where in the save was successful but post save either the asset may not exist
            // or the user may not have permission to view the asset. For instance, the current user may remove the
            // "view" permission security policy for himself.
            if (!success && status == 404)
            {
                success = true;			
            }

            if (!success && response && response.exception_body) {
                var error = response.exception_body.message;
                otui.NotificationManager.showNotification({
                    'message' : error,
                    'status' : 'error',
                    'stayOpen' : true
                });
            }
            if(success){
                var msg = otui.tr('Successfully synced the SAP Commerce Cloud data');
                var notification = {
                    'message': msg,
                    'status': 'ok',
                    'stayOpen': false
                };
                otui.NotificationManager.showNotification(notification);               
            }
            OTDCPWSyncView.unblockViews(self,parentView);
            //Unlock the asset and route to detail view
            otui.services.asset.unlock({'assetID' : folderID}, function(response, status, success) {
                var params = {};
                params["resourceID"] = folderID;
                parentView.callRoute("show",params,true);  
            });  
                   
        });	
    }
 
})(window);