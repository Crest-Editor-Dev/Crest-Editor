import { initialize, isWorkerInitialized } from './internal/common/initialize';
import * as worker from './core/vs/editor/editor.worker.start.js';

self.onmessage = () => {
	if (!isWorkerInitialized()) {
		worker.start(() => {
			return {};
		});
	}
};

export { initialize };
