import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { RestCallService } from 'src/app/services/rest-call.service';
import { WindowSizeService } from 'src/app/services/window-size-service';
import { LoadingService } from 'src/app/services/loading.service';
import { MenubarComponent } from 'src/app/components/menubar/menubar.component';
import { SidebarComponent } from 'src/app/components/sidebar/sidebar.component';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, RouterModule, MenubarComponent, SidebarComponent],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.less']
})
export class AdminComponent implements OnInit, OnDestroy {

  isMobileDevice = window.screen.width <= 900;
  isLoading = false;
  loadingText = '';
  private resizeSubscription: Subscription;
  private loadingSubscription: Subscription;

  constructor(private sizeService: WindowSizeService,
	private restCallService: RestCallService,
    private loadingService: LoadingService,
    private cdr: ChangeDetectorRef) {

  }

  ngOnInit() {
	// Async GET API call to /damuiconfig/initConfig using RestCallService
	  this.restCallService.get('Loading config...', '/damuiconfig/initConfig').subscribe({
		  next: (response) => {
			  console.log('/damuiconfig/initConfig API call successful:', response.body);
		  },
		  error: (error) => {
			  console.error('/damuiconfig/initConfig API call failed:', error);
		  }
	  });
	
    this.loadingSubscription = this.loadingService.loaderActive$.subscribe(details => {
      this.isLoading = details.active;
      this.loadingText = details.text;
      this.cdr.detectChanges();
    });
    this.resizeSubscription = this.sizeService.onResize$.subscribe(mobileDevice => this.isMobileDevice = mobileDevice);
  }

  ngOnDestroy() {
    if (this.resizeSubscription) {
      this.resizeSubscription.unsubscribe();
    }

    if (this.loadingSubscription) {
      this.loadingSubscription.unsubscribe();
    }
  }

}
