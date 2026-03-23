import { ComponentCanDeactivate } from './component.deactivate';

export abstract class FormCanDeactivate extends ComponentCanDeactivate {
    abstract get unSavedData(): boolean;

    canDeactivate(): boolean {
        return !this.unSavedData;
    }
}
