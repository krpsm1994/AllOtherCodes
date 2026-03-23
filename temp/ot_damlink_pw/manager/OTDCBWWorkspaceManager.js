(function(exports) {
    var OTDCBWWorkspaceManager = exports.OTDCBWWorkspaceManager = function OTDCBWWorkspaceManager(){ 
	};

    OTDCBWWorkspaceManager.getRootConfigs = function getRootConfigs(callback){
        var session = jQuery.parseJSON(sessionStorage.session);
        $.ajax({
            type: "POST",
			url: "/ot-damlink/api/workspace/config?type=bw",
			data: {
				loginName: session.login_name,
				sessionId: session.id,
				messageDigest: session.message_digest,
				validationKey: session.validation_key,
				queryLang: otui.locale(),
				userId: session.user_id
			},
			async: true,
			dataType: "json",
            success: function (response) {
                OTDCBWWorkspaceManager.config = {};
				OTDCBWWorkspaceManager.root_folder = response.otdcbw.rootFolder.id;
				OTDCBWWorkspaceManager.isInitialized = response.otdcbw.isInitialized;
                OTDCBWWorkspaceManager.config.facetConfig = response.otdcbw.facetConfig || OTDCUtilities.defaultBWFacetName;
                OTDCBWWorkspaceManager.config.assetFacetConfig = response.otdcbw.assetFacetConfig || OTDCUtilities.defaultBWAssetFacetName;
                OTDCBWWorkspaceManager.config.AssignToWorkspace={};
                OTDCBWWorkspaceManager.config.AssignToWorkspace.maxWorkspaces = response.otdcbw.assignToWorkspace.maxWorkspaces;
                OTDCBWWorkspaceManager.subFolder_name = response.otdcbw.workspace.defaultSubfolder.name;
                callback();
            },
            error: function(response){
                callback();
            }
        });
    }
})(window);