(function (cxt) {
	/**
	 * PW Homeview sidebar view displays the list filters.
	 * 
	 * <br/>
	 * 
	 * @class OTDCPWSidebarView
	 * @mixes withAccordion
	 * @constructor
	 * @param {OTDCPWSidebarView.properties} props Properties for this view
	 */
	var OTDCPWSidebarView = cxt.OTDCPWSidebarView = otui.define("OTDCPWSidebarView", ["withAccordion"], function () {
		
		// To open the accordion by default.
		var open = [0];
		this.properties = {'type' : 'damlinkpw_sidebar', 
							'name' : "damlinkpw_sidebar", 
							'open':open, 
							'searchConfigID': "", 
							'nodeID': "",
							'appliedFacets': JSON.stringify({'facets': []})
						};
			
		this._initContent = function _initContent(self, callback) {
			var template = self.getTemplate("content");
			var content = $(template);

			if(self.properties.nodeID)
				self.getChildView(OTDCPWFacetsView).properties.needsUpdate = true;

			searchConfigList = SearchManager.searchConfigurations.response.search_configurations_resource.search_configuration_list;

			//this.properties.searchConfigID = "2";
			//this.properties.nodeID = OTDCPWWorkspaceManager.root_folder;

			callback(content);
		};
		
		this.hierarchy =[{'view' : OTDCPWFacetsView}];


		this.watch('nodeID', function(newVal, oldVal)
		{
			if(newVal != oldVal)
			{
				if(this.properties.open[0] == 0)
				{
					this.getChildView(OTDCPWFacetsView).properties.facetsRendered = false;
					this.getChildView(OTDCPWFacetsView).properties.needsUpdate = true;
				}
				else
					this.getChildView(OTDCPWFacetsView).updateFacets();
			}
		});
		
		this.watch('appliedFacets', function(newVal, oldVal){
			if(newVal != oldVal)
			{
				if(!this.getChildView(OTDCPWFacetsView).properties.needsUpdate)
					this.getChildView(OTDCPWFacetsView).updateFacets(JSON.parse(newVal).facets);
			}
		});
		
		// this.watch('filterTerm', function(newVal, oldVal)
		// {
		// 	if(oldVal && newVal != oldVal)
		// 	{
		// 		var advancedSearch = this.properties.advancedSearch ? JSON.parse(this.properties.advancedSearch) : undefined;
		// 		advancedSearch = advancedSearch ? !!(advancedSearch.search_condition_list) : false;
				
		// 		if(!advancedSearch)
		// 			this.updateFacets();
		// 	}
		// });
		
		// this.watch('searchConfigID', function(newVal, oldVal){
		// 	if(newVal != "none" || (newVal == "none" && (oldVal != "none" && oldVal != undefined)))
		// 	{
		// 		this.updateFacets();
		// 	}
		// });

		this.updateFacets = function()
		{
            this.getChildView(OTDCPWFacetsView).updateFacets(JSON.parse(this.properties.appliedFacets).facets);
		};
	});
	
})(window);