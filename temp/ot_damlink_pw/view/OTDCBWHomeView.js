function OTDCBWHomeViewBootstrap(exports) {

    var OTDCBWHomeView = exports.OTDCBWHomeView = otui.extend("ResultsView", "OTDCBWHomeView", function () {
        this._initContent = function initOTDCBWHomeView(self, callback) {
            self.storedProperty("title", otui.tr("EAM Dashboard"));
			self.storedProperty("sortOptions", PageManager.folderSortOptions || []);
            self.storedProperty("showSortOptions", true);
			self.storedProperty("extraSortOptions", PageManager.searchSortOptions || []);
			self.storedProperty("showRefresh", true); 
			self.storedProperty("showLocalSearch", true);
            ResultsView._initContent.apply(this, arguments);
        };

        this.properties =
        {
            'folderID': OTDCBWWorkspaceManager.root_folder,
            'nodeID': OTDCBWWorkspaceManager.root_folder,
            'service': 'damgetbwfolders',
            'serviceData': {},
            'sourceName': 'workspaces',
            'templateName': PageManager.getResultsViewTemplate(),
            'lazyLoad': true,
            'savedSearchID': "none",
            'searchConfigID': "none",
            'searchScopeID': "none",
            'appliedFacets': JSON.stringify({ 'facets': [] }),
            'no-results-msg': otui.tr("No EAM Workspaces Found"),
			'defaultSortField': PageManager.defaultSortField
        };

        if (otui.is(otui.modes.DESKTOP)) {
            this.properties.showPagination = true;
            this.properties.showLatestPagination = true;
            this.properties.showAssetsPerPageSetting = true;
        }

        this.selectionContext = {
            'type': 'com.artesia.asset.selection.FolderSelectionContext',
            'map': { 'folder_id': 'nodeID' },
            'alwaysUse': false,
            'includeFolderDescendents': true
        };

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
        
        this.bind("single-spa-results-loaded", function () {
            let otResults = this.contentArea()[0].querySelector(".ot-asset-results");
            if (otResults) {
                let view = this;
                $(otResults).on("applied-facets-change", function (event) {
                    let eventDetail = event.detail;
                    if (eventDetail.appliedFacets.length === 0) {
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

    let bwFolderContentsRouter = function (req, routeParts, routeData) {
        localStorage.setItem(OTDCUtilities.currentContextName, OTDCUtilities.BWContextName);
        return {
            'name': 'resultslist_' + (req.params.nodeID || req.params.folderID) + "_" + req.params.resourceID,
            'folderID': (req.params.nodeID || req.params.folderID),
            'templateName': PageManager.getResultsViewTemplate()
        };
    };

    let pageprop = {
        'assetsPerPage': PageManager.assetsPerPage,
        'page': "0",
        'sortFieldforFacets': PageManager.DEAFAULT_CHECKED_OUT_SORT_FIELD,
        'sortField': "NAME",
        'sortPrefix': PageManager.ascendingSortPrefix
    };

    OTDCBWHomeView.getDefaultPageProperties = function getDefaultPageProperties() {
        return pageprop;
    }

    OTDCBWHomeView.getBW = function getBW() {
        homeview = OTDCBWHomeView.route.use("open", {...OTDCBWHomeView.ModernUIHeaderParameters, 'pageTitle': otui.tr('EAM dashboard')});
        homeview('open', OTDCBWHomeView.bwFolderParams, {...OTDCBWHomeView.ModernUIHeaderParameters, 'pageTitle': otui.tr('EAM dashboard')});
        localStorage.setItem(OTDCUtilities.currentContextName, OTDCUtilities.BWContextName);
    };

    OTDCBWHomeView.gearOnClick = function (event, target, currentView) {
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

        var visible = pageOptions.visible;

        if (visible) {
            pageOptions.hide();
        }
        else {
            var displayViewPrefValArray = otui.PreferencesManager.getPreferenceDataById('ARTESIA.PREFERENCE.GALLERYVIEW.DEFAULT_VIEW_MODE');

            $(pageOptions).find(".ot-metadata-display-section").addClass("ot-hide-block");
            $(pageOptions).find(".ot-watermark-display-section").addClass("ot-hide-block");

            pageOptions.show(target);
            xtag.requestFrame(_.debounce(function () {
                displayViewPrefValArray = otui.PreferencesManager.getPreferenceDataById('ARTESIA.PREFERENCE.GALLERYVIEW.DEFAULT_VIEW_MODE');
                var type = displayViewPrefValArray[0].values[0];
                var templateElem = $(".ot-page-options-section div.ot-gallery-template-type");
                var highlightElem = $("div[data-template-name=" + type + "]")[0];

                for (var i = 0; i < templateElem.length; i++) {
                    xtag.removeClass(templateElem[i], "hightlight");
                }
                if (highlightElem)
                    xtag.addClass(highlightElem, "hightlight");
            }, 100));
        }
        event.stopImmediatePropagation();
        registerKeyEventsForPageOptions();
    };

    OTDCBWHomeView.ModernUIHeaderParameters = {'isModernResultsView': true, 'showBreadcrumbPanel': false};

    OTDCBWHomeView.route.base = "/:nodeID@:pageProperties?@:filterTerm?@:metadataLocale?@:appliedFacets?@";

    OTDCBWHomeView.route.define("go", OTDCBWHomeView.route.as("OTDCEAMDashboard" + OTDCBWHomeView.route.base,
        // Load the layout template
        otui.withTemplate("main-with-sidebar-actionbar"),
        OTDCPWSidebarView.route.to(),
        OTDCBWHomeView.route.to(bwFolderContentsRouter),
        {...OTDCBWHomeView.ModernUIHeaderParameters, 'pageTitle': otui.tr('EAM Dashboard')}
    ));

    OTDCBWHomeView.route.define("open-folder", OTDCPWDetailView.route.use("open"), OTDCBWHomeView.ModernUIHeaderParameters);

    //Folder Action - Show properties
    OTDCBWHomeView.route.define("open-folder-contents", OTDCPWDetailView.route.use("show", true), OTDCBWHomeView.ModernUIHeaderParameters);

    //Folder Action - Edit Properties		
    OTDCBWHomeView.route.define("edit-folder-contents", OTDCPWDetailEditView.route.use("edit", true), OTDCBWHomeView.ModernUIHeaderParameters);

    OTDCPWDetailView.route.define("cancelBW", OTDCBWHomeView.getBW, OTDCBWHomeView.ModernUIHeaderParameters);

    OTDCBWHomeView.route.define("change-page", function (routeName, params) {
        this.properties.pageProperties = params.pageProperties;
    }, OTDCBWHomeView.ModernUIHeaderParameters);

    OTDCBWHomeView.route.inherit();

    otui.registerService("damgetbwfolders", {
        'read': function getRecentAssets(data, callback, view) {
            if(!view) view = otui.main.getChildView(OTDCBWHomeView);
            var msg;
            if (OTDCBWWorkspaceManager.isInitialized === "false") {
                msg = otui.tr("EAM workspace initialization is not done. Please contact your administrator.");
                otui.NotificationManager.showNotification({
                    message: msg,
                    stayOpen: false,
                    status: "warning"
                });
                view.storedProperty("no-results-msg", "EAM workspace initialization is not done. Please contact your administrator.");
                callback();
                return;
            }
            if (!OTDCBWWorkspaceManager.root_folder) {
                msg = otui.tr("EAM root folder is not found. Please contact your administrator");
                otui.NotificationManager.showNotification({
                    message: msg,
                    stayOpen: false,
                    status: "warning"
                });
                return;
            }
            if (!otui.UserFETManager.isTokenAvailable("OTDCBW.VIEW")) {
                view.storedProperty("no-results-msg", otui.tr("You do not have permission to view EAM workspaces."));
                callback();
                return;
            }

            view.blockContent();
            var nodeID = data.nodeID;
            var pageID = data.pageID || nodeID;
            //Overriding sort field
            data.pageProperties.sortField = "NAME";
            PageManager.savePageData(pageID, data.pageProperties);
            var preferenceID = PageManager.getFieldPreferenceName();
            var extraFields = PageManager.getExtraFields();
            searchConfigList = SearchManager.searchConfigurations.response.search_configurations_resource.search_configuration_list;

            data.searchConfigID = 2;

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
                                                                                              
            let facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).facetConfig || OTDCUtilities.defaultBWFacetName;
            if (hasFacets) {
                if (!OTDCPWFacetsManager.currentFacetConfiguration) {
                    facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).facetConfig || OTDCUtilities.defaultBWFacetName;
                    OTDCPWFacetsManager.readFacets(facetConfigurationName, OTDCUtilities.defaultBWFacetName, function () {
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
                OTDCUtilities.doFolderChildrenRead(data, sendResults, view);
            }
            view.trigger("reload-data");
        }
    });

    OTDCBWHomeView.bwFolderParams = undefined;
    OTDCBWHomeView.defaultBWHomeFolder = function defaultBWHomeFolder() {
        OTDCBWHomeView.bwFolderParams = { 'nodeID': OTDCBWWorkspaceManager.root_folder, 'appliedFacets': undefined, 'filterTerm': "", pageProperties: undefined, 'searchConfigID': "none", 'searchScopeID': "none" };
        OTDCBWHomeView.bwFolderParams.pageProperties = pageprop;
    }

    //Create a menu item for our new view
    otui.ready(function () {
        if (otui.UserFETManager.isTokenAvailable("OTDCBW.VIEW") && OTDCBWWorkspaceManager.isInitialized === "true") {
            OTDCBWHomeView.defaultBWHomeFolder();
            otui.Menus.register({
                'name': 'OTDCBWMenu',
                'icon': { "desktop": "./ot_damlink_pw/style/img/hamburger_eam_dashboard_on.svg" },
                'title': otui.tr("EAM Dashboard"),
                'select': OTDCBWHomeView.getBW,
				'matchPattern' : "p=OTDCEAMDashboard"
            });
        }
    }, true);
};

otui.ready(function () {
    //Load Workspace configs and in the callback, call the Home View
    OTDCBWWorkspaceManager.getRootConfigs(function () {
        OTDCBWHomeViewBootstrap(window);
    });

});