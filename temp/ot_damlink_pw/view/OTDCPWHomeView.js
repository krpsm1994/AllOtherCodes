function OTDCPWHomeViewBootstrap(exports) {
	/**
	 * Main constructor for OTDCPWHomeView.
	 * Sets up properties, selection context, event bindings, and view logic.
	 */
	let OTDCPWHomeView = exports.OTDCPWHomeView = otui.extend("ResultsView", "OTDCPWHomeView", function () {


		/**
		 * Initializes the content for the Product Dashboard view.
		 * Sets up properties and calls the parent ResultsView initializer.
		 */
		this._initContent = function initOTDCPWHomeView(self, callback) {
			self.storedProperty("title", otui.tr("Product Dashboard"));
			self.storedProperty("sortOptions", PageManager.folderSortOptions || []);
			self.storedProperty("newActionsLookupName", "OTDCPWHomeHeaderActions");
			self.storedProperty("showSortOptions", true);
			self.storedProperty("extraSortOptions", PageManager.searchSortOptions || []);
			self.storedProperty("showRefresh", true);
			self.storedProperty("showLocalSearch", true);
			ResultsView._initContent.apply(this, arguments);
		};


		this.properties =
		{
			'folderID': OTDCPWWorkspaceManager.root_folder,
			'nodeID': OTDCPWWorkspaceManager.root_folder,
			'service': 'damgetfolders',
			'serviceData': {},
			'sourceName': 'workspaces',
			'templateName': PageManager.getResultsViewTemplate(),
			'lazyLoad': true,
			'savedSearchID': "none",
			'searchConfigID': "none",
			'searchScopeID': "none",
			'appliedFacets': JSON.stringify({ 'facets': [] }),
			'no-results-msg': otui.tr("No Workspaces Found"),
			'defaultSortField': "NAME"
		};

		if (otui.is(otui.modes.DESKTOP)) {
			this.properties.showPagination = true;
			this.properties.showLatestPagination = false;
			this.properties.showAssetsPerPageSetting = true;
		}

		//This is needed for Select All pages option
		this.selectionContext = {
			'type': 'com.artesia.asset.selection.FolderSelectionContext',
			'map': { 'folder_id': 'nodeID' },
			'alwaysUse': false,
			'includeFolderDescendents': true
		};

		/**
         * Creates and sets the selection context property for the view.
         * Used for REST API calls and selection logic.
         */
		function createSelectionContext() {
			// Create the selection context object
			let selectionContext = {
				'type': 'com.artesia.asset.selection.FolderSelectionContext',
				'map': { 'folder_id': 'nodeID' },
				'alwaysUse': false,
				'facetRestrictionList': [],
			}

			// Then wrap it in a format that the REST API expects and set it as a property.
			this.properties.selectionContext = { 'selection_context_param': { 'selection_context': selectionContext } };

		};

		// used for page navigation
		this.idProperty = 'nodeID';

		this.serviceDataMap = {
			'nodeID': 'folderID', 'pageProperties': 'pageProperties',
			'appliedFacets': 'appliedFacets', 'searchConfigID': 'searchConfigID',
			'searchScopeID': 'searchScopeID', 'metadataLocale': 'metadataLocale',
			'savedSearchID': 'savedSearchID', 'selection': 'selectionContext'

		};
		this.searchPropsMap = {
			'searchID': 'searchID', 'appliedFacets': 'appliedFacets',
			'searchConfigID': 'searchConfigID', 'searchScopeID': 'searchScopeID',
			'metadataLocale': 'metadataLocale', 'editedSavedSearch': 'editedSavedSearch',
			'savedSearchID': 'savedSearchID'
		};

        /**
         * Event handler for reloading data.
         * Updates sidebar facets and triggers facet updates.
         */
		this.bind("reload-data", function () {
			if (window['OTDCPWSidebarView']) {
				let otdcpwSidebarView = otui.main.getChildView(OTDCPWSidebarView);
				if (otdcpwSidebarView) {
					//Update sidebar facets when view is reloaded, same as Homeview
					otdcpwSidebarView.properties.appliedFacets = this.properties.appliedFacets;
					let facets = document.querySelector(".ot-facets-root .ot-facet");
					if (facets) {
						SearchManager.clearSearchForKeyword(this.properties.nodeID);
						otdcpwSidebarView.updateFacets();
					}
				}
			}
		});

        /**
         * Event handler for when results are loaded.
         * Binds facet change events to the results area.
         */
		this.bind("single-spa-results-loaded", function () {
			let otResults = this.contentArea()[0].querySelector(".ot-asset-results");
			if (otResults) {
				let view = this;
				$(otResults).on("applied-facets-change", function (event) {
					let eventDetail = event.detail;
					if (event.detail.appliedFacets.length === 0) {
						OTDCPWFacetsView.handleClearFilters(view);
					} else if (eventDetail.isLocalSearch) {
						OTDCPWFacetsView.performKeywordSearch(view, eventDetail);
					} else {
						OTDCPWFacetsView._removeFacet(view, eventDetail);
					}
				});
			}
		});

		this.createClearButton = function () {
			let div = document.createElement("div");
			div.setAttribute("class", "otdcpw-facets-clear-cont");
			let button = document.createElement("button");
			button.setAttribute("class", "ot-button secondary");
			button.textContent = otui.tr("Clear");
			button.setAttribute("aria-label", otui.tr("Clear Filters"));
			button.setAttribute("title", otui.tr("Clear Filters"));
			button.addEventListener("click", function (event) {
				OTDCPWFacetsView.handleClearFilters();
			});
			div.append(button);
			this.contentArea().find(".ot-collections-tracker-chicklets").append(div);
		}

		return createSelectionContext;
	});

    /**
     * Router for folder contents.
     * Sets context and returns routing parameters for the results view.
     */
	let folderContentsRouter = function (req, routeParts, routeData) {
		localStorage.setItem(OTDCUtilities.currentContextName, OTDCUtilities.PWContextName);
		return {
			'name': 'resultslist_' + (req.params.nodeID || req.params.folderID) + "_" + req.params.resourceID,
			'folderID': (req.params.nodeID || req.params.folderID),
			'templateName': PageManager.getResultsViewTemplate()
		};
	};
	let pageprop = PageManager.createPageProperties(0, PageManager.assetsPerPage, PageManager.descendingSortPrefix, PageManager.defaultAssetAuditSortField)
    
	/**
     * Returns default page properties for the view.
     */
	OTDCPWHomeView.getDefaultPageProperties = function getDefaultPageProperties() {
		return pageprop;
	}
    
	/**
     * Opens the Product Dashboard view.
     * Sets up routing and context.
     */
	OTDCPWHomeView.getPW = function getPW() {
		let homeview = OTDCPWHomeView.route.use("open", { ...OTDCPWHomeView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Product Dashboard') });
		homeview('open', OTDCPWHomeView.pwFolderParams, OTDCPWHomeView.ModernUIHeaderParameters);
		localStorage.setItem(OTDCUtilities.currentContextName, OTDCUtilities.PWContextName);
	};

    /**
     * Handles click on the settings (gear) icon.
     * Shows/hides display section and manages UI state.
     */
	OTDCPWHomeView.gearOnClick = function (event, target, currentView) {
		let view = currentView;
		if (!view) {
			view = otui.Views.containing(event.currentTarget);
		}
		let contentArea = view.contentArea();
		let pageOptions = $('.ot-page-options', contentArea);

		// If page options isn't found within the view, see if it's visible elsewhere.
		if (!pageOptions.length) {
			pageOptions = $('.ot-page-options[viewid="' + view.id() + '"]');
		}

		pageOptions = pageOptions[0];
		$(pageOptions).find(".ot-display-view-section").addClass("otdcpw-home-gear-displaysection");

		$(pageOptions).find(".ot-sort-by-section").addClass("ot-hide-block");
		$(pageOptions).find(".ot-assets-per-page-section").addClass("ot-hide-block");
		$("#ot-sort-ascending").attr("disabled", true);
		$("#ot-sort-descending").attr("disabled", true);
		$("#ot-page-options-sort-direction-section").hide();


		target = target || event.currentTarget;

		let visible = pageOptions.visible;

		if (visible) {
			pageOptions.hide();
		}
		else {
			let displayViewPrefValArray = otui.PreferencesManager.getPreferenceDataById('ARTESIA.PREFERENCE.GALLERYVIEW.DEFAULT_VIEW_MODE');

			$(pageOptions).find(".ot-metadata-display-section").addClass("ot-hide-block");
			$(pageOptions).find(".ot-watermark-display-section").addClass("ot-hide-block");

			pageOptions.show(target);
			xtag.requestFrame(_.debounce(function () {
				displayViewPrefValArray = otui.PreferencesManager.getPreferenceDataById('ARTESIA.PREFERENCE.GALLERYVIEW.DEFAULT_VIEW_MODE');
				let type = displayViewPrefValArray[0].values[0];
				let templateElem = $(".ot-page-options-section div.ot-gallery-template-type");
				let highlightElem = $("div[data-template-name=" + type + "]")[0];

				for (const elem of templateElem) {
					xtag.removeClass(elem, "hightlight");
				}
				if (highlightElem)
					xtag.addClass(highlightElem, "hightlight");
			}, 100));
		}
		event.stopImmediatePropagation();
		registerKeyEventsForPageOptions();
	};
	//---- settings icon (display section) code end----

	// Specify the route for the view,
	// binding an optional "display" parameter to the URL
	// The route should then load the "main" template
	// using the full-page for content (no side-bar)

	OTDCPWHomeView.ModernUIHeaderParameters = { 'isModernResultsView': true, 'showBreadcrumbPanel': false };

	// Define the URL format pattern

	OTDCPWHomeView.route.base = "/:nodeID@:pageProperties?@:filterTerm?@:metadataLocale?@:appliedFacets?@";

	let csOpenView = OTDCPWHomeView.route.as("otdcDashboard" + OTDCPWHomeView.route.base,
		// Load the layout template
		otui.withTemplate("main-with-sidebar-actionbar"),
		OTDCPWSidebarView.route.to(),
		OTDCPWHomeView.route.to(folderContentsRouter),
		{ ...OTDCPWHomeView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Product Dashboard') }
	);

	// Then bind that route URL into the named route "open".
	OTDCPWHomeView.route.define("go", csOpenView);

	OTDCPWHomeView.route.define("open-folder", OTDCPWDetailView.route.use("open"), OTDCPWHomeView.ModernUIHeaderParameters);

	//Folder Action - Show properties
	OTDCPWHomeView.route.define("open-folder-contents", OTDCPWDetailView.route.use("show", true), OTDCPWHomeView.ModernUIHeaderParameters);

	//Folder Action - Edit Properties		
	OTDCPWHomeView.route.define("edit-folder-contents", OTDCPWDetailEditView.route.use("edit", true), { ...OTDCPWHomeView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Edit Workspace') });
	//Cancel in Detail View
	OTDCPWDetailView.route.define("cancelPW", OTDCPWHomeView.getPW, { ...OTDCPWHomeView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Product Dashboard') });
	//Route for pagination
	OTDCPWHomeView.route.define("change-page", function (routeName, params) {
		this.properties.pageProperties = params.pageProperties;
	}, OTDCPWHomeView.ModernUIHeaderParameters);

	OTDCPWHomeView.route.inherit();


    /**
     * Data service for reading DAM folders.
     * Handles permissions, initialization, and data retrieval logic.
	 *  Register a new data service with the OTUI framework.
     */
	otui.registerService("damgetfolders", {
		'read': function getRecentAssets(data, callback, view) {
			if (!view) view = otui.main.getChildView(OTDCPWHomeView);
			//Fix for pagination popup issue
			let pageNavSizeOptions = $('#pageNavSizeOptions');
			if (pageNavSizeOptions && pageNavSizeOptions.length > 0) {
				pageNavSizeOptions.removeClass('show');
			}
			//Check if workspace rootconfig is initialized, else throw error
			let msg;
			if (OTDCUtilities.isPWContext()) {
				if (OTDCPWWorkspaceManager.isInitialized === "false") {
					msg = otui.tr("Product workspace initialization is not done. Please contact your administrator.");
					otui.NotificationManager.showNotification({
						message: msg,
						stayOpen: false,
						status: "warning"
					});
					view.storedProperty("no-results-msg", otui.tr("Product workspace initialization is not done. Please contact your administrator."));
					callback([], false, 0);
					return;
				}
				if (!OTDCPWWorkspaceManager.root_folder) {
					msg = otui.tr("Product Workspace root folder is not found. Please contact your administrator.");
					otui.NotificationManager.showNotification({
						message: msg,
						stayOpen: false,
						status: "warning"
					});
					return;
				}
				//returning if the user does not have OTDC.PW.VIEW FET.
				if (!otui.UserFETManager.isTokenAvailable("OTDC.PW.VIEW")) {
					view.storedProperty("no-results-msg", otui.tr("You do not have permission to view workspaces."));
					callback([], false, 0);
					return;
				}
			} else if (OTDCUtilities.isEAMContext()) {
				if (OTDCBWWorkspaceManager.isInitialized === "false") {
					msg = otui.tr("EAM workspace initialization is not done. Please contact your administrator.");
					otui.NotificationManager.showNotification({
						message: msg,
						stayOpen: false,
						status: "warning"
					});
					view.storedProperty("no-results-msg", otui.tr("EAM workspace initialization is not done. Please contact your administrator."));
					callback([], false, 0);
					return;
				}
				if (!OTDCBWWorkspaceManager.root_folder) {
					msg = otui.tr("EAM Workspace root folder is not found. Please contact your administrator");
					otui.NotificationManager.showNotification({
						message: msg,
						stayOpen: false,
						status: "warning"
					});
					return;
				}
				//returning if the user does not have OTDC.PW.VIEW FET.
				if (!otui.UserFETManager.isTokenAvailable("OTDCBW.VIEW")) {
					view.storedProperty("no-results-msg", otui.tr("You do not have permission to view workspaces."));
					callback([], false, 0);
					return;
				}
			}

			view.blockContent();
			let nodeID = data.nodeID;
			let pageID = data.pageID || nodeID;
			//Overriding sort field
			data.pageProperties.sortField = "NAME";
			PageManager.savePageData(pageID, data.pageProperties);
			let preferenceID = PageManager.getFieldPreferenceName();
			let extraFields = PageManager.getExtraFields();

			data.searchConfigID = 2;
			let appliedFacets = { 'facets': [] };
			if (data.appliedFacets)
				appliedFacets = JSON.parse(data.appliedFacets);

			let hasFacets = !!(appliedFacets.facets.length);
			let sendResults = function sendResults(response, success) {
				view.unblockContent();
				if (!success) {
					callback(response, success, 0);
				} else {
					let totalItems = view.properties.folderData?.container_child_counts?.total_child_count;
					callback(response, success, totalItems || 0);
					$('input[type="search"].uxa-text-input').attr('placeholder', otui.tr('Search within workspaces'));
					document.querySelectorAll('.uxa-multi-value-chip-label').forEach(function (label) {
						if (label.textContent.includes("Search for")) {
							label.closest('.uxa-multi-value-chip').style.display = 'none';
						}
					});
				}
			}
			//workaround; sometime pageProperties field values are set to text "undefined" (not keyword undefined)
			if (!data.pageProperties.page || data.pageProperties.page === 'undefined') {
				data.pageProperties = pageprop;
			}
			let facetConfigurationName = (OTDCPWWorkspaceManager.config || {}).facetConfig || OTDCUtilities.defaultPWFacetName;
			if (hasFacets) {
				if (!OTDCPWFacetsManager.currentFacetConfiguration) {
					OTDCPWFacetsManager.readFacets(facetConfigurationName, OTDCUtilities.defaultPWFacetName, function () {
						OTDCPWSearchManager.getFolderAssetsByKeyword("*", data.pageProperties.page, PageManager.assetsPerPage, preferenceID, extraFields, data, function (results, totalItems, success) {
							PageManager.numTotalChildren[pageID] = totalItems;
							sendResults(results, success);
						});
					});
				} else {
					OTDCPWSearchManager.getFolderAssetsByKeyword("*", data.pageProperties.page, PageManager.assetsPerPage, preferenceID, extraFields, data, function (results, totalItems, success) {
						PageManager.numTotalChildren[pageID] = totalItems;
						sendResults(results, success);
					});
				}
			} else {
				view.properties.searchID = "*";
				if (!view) view = otui.main.getChildView(OTDCPWHomeView);
				OTDCUtilities.doFolderChildrenRead(data, sendResults, view);
			}
			view.trigger("reload-data");
		}
	});  // END: service definition/registration

	OTDCPWHomeView.pwFolderParams = undefined;

	OTDCPWHomeView.defaultPWHomeFolder = function defaultPWHomeFolder() {
		OTDCPWHomeView.pwFolderParams = { 'nodeID': OTDCPWWorkspaceManager.root_folder, 'appliedFacets': undefined, 'filterTerm': "", pageProperties: undefined, 'searchConfigID': "none", 'searchScopeID': "none" };
		OTDCPWHomeView.pwFolderParams.pageProperties = pageprop;
	}

	/**
	 * Loads workspace configs and bootstraps the Home View when ready.
	 * Create a menu item for our new view
	 */
	otui.ready(function () {
		if (otui.UserFETManager.isTokenAvailable("OTDC.PW.VIEW") && OTDCPWWorkspaceManager.isInitialized === "true") {
			OTDCPWHomeView.defaultPWHomeFolder();
			otui.Menus.register({
				'name': 'OTDCPWMenu',
				'title': otui.tr("Product Dashboard"),
				'icon': { "desktop": "./ot_damlink_pw/style/img/hamburger_product_dashboard_on.svg" },
				'select': OTDCPWHomeView.getPW,
				'matchPattern': "p=otdcDashboard"
			});
		}
	}, true);
};

otui.ready(function () {
	//Load Workspace configs and in the callback, call the Home View
	OTDCPWWorkspaceManager.getRootConfigs(function () {
		OTDCPWHomeViewBootstrap(window);
	});

});