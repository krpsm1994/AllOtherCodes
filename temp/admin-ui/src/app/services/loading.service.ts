import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export class LoadingDetails {
  active: boolean;
  text: string = '';
}

@Injectable({
  providedIn: 'root',
})
export class LoadingService {

  private isLoading: boolean;
  private message: string;
  private loadingSubject = new BehaviorSubject<LoadingDetails>({ active: false, text: '' });

  constructor() {
  }

  show(text: string) {
    this.isLoading = true;
    this.message = text;
    this.loadingSubject.next({ active: this.isLoading, text: this.message });
  }

  hide() {
    this.isLoading = false;
    this.message = '';
    this.loadingSubject.next({ active: this.isLoading, text: this.message });
  }

  get loaderActive$(): Observable<LoadingDetails> {
    return this.loadingSubject.asObservable();
  }
}
